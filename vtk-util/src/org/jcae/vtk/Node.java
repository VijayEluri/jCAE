/*
 * Project Info:  http://jcae.sourceforge.net
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 *
 * (C) Copyright 2008, by EADS France
 */
package org.jcae.vtk;


import gnu.trove.TIntArrayList;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import vtk.vtkActor;
import vtk.vtkExtractSelectedPolyDataIds;
import vtk.vtkIdTypeArray;
import vtk.vtkIntArray;
import vtk.vtkLookupTable;
import vtk.vtkPolyData;
import vtk.vtkPolyDataMapper;
import vtk.vtkSelection;

/**
 *
 * @author Julian Ibarz
 */
public class Node extends AbstractNode
{
	private static final Logger LOGGER = Logger.getLogger(Node.class.getName());
	
	private ArrayList<AbstractNode> children = new ArrayList<AbstractNode>();
	// Datas if the node manage
	private TIntArrayList offsetsVertices;
	private TIntArrayList offsetsLines;
	private TIntArrayList offsetsPolys;
	private int nbrOfVertices;
	private int nbrOfLines;
	private int nbrOfPolys;
	
	// Lookup table for color of leaves
	private vtkLookupTable table;
	
	private ArrayList<ChildCreationListener> childCreationListeners = new ArrayList<ChildCreationListener>();

	private static class NodeData extends LeafNode.DataProvider
	{
		NodeData(float[] nodes, float[] normals, int nbrOfVertices, int[] vertices, int nbrOfLines, int[] lines, int nbrOfPolys, int[] polys)
		{
			this.nodes = nodes;
			this.normals = normals;
			this.nbrOfVertices = nbrOfVertices;
			this.vertices = vertices;
			this.nbrOfLines = nbrOfLines;
			this.lines = lines;
			this.nbrOfPolys = nbrOfPolys;
			this.polys = polys;
		}

		@Override
		public float[] getNormals()
		{
			return normals;
		}

		@Override
		public int[] getLines()
		{
			return lines;
		}

		@Override
		public int[] getPolys()
		{
			return polys;
		}

		@Override
		public int[] getVertices()
		{
			return vertices;
		}

		@Override
		public float[] getNodes()
		{
			return nodes;
		}

		@Override
		public void load()
		{
			// Do nothing
		}

		@Override
		public void unLoad()
		{
			// Do nothing
		}
	}

	public Node(Node parent)
	{
		super(parent);
		
		if(parent != null)
		{
			parent.addChild(this);
			// Inherits child creation listeners
			childCreationListeners.addAll(parent.childCreationListeners);
		}
	}

	LeafNode getLeafNodeFromCell(int cellID)
	{
		if (!isManager())
			throw new RuntimeException("Node is not a manager");
		if (cellID < 0 || cellID >= data.GetNumberOfCells())
			throw new RuntimeException("cellID out of bounds");
		
		List<LeafNode> leaves = getLeaves();
		int ID = ((vtkIntArray) data.GetCellData().GetScalars()).GetValue(cellID);

		return leaves.get(ID);
	}

	/**
	 * Set pickable the actor of the node. If the node is not a manager,
	 * it processes its children recursively.
	 * @param pickable
	 */
	@Override
	public void setPickable(boolean pickable)
	{
		if (isManager())
		{
			super.setPickable(pickable);
			return;
		}

		for(AbstractNode child : children)
			child.setPickable(pickable);
	}
	
	public List<LeafNode> getLeaves()
	{
		// Do not keep the leaves, just compute
		ArrayList<LeafNode> toReturn = new ArrayList<LeafNode>();

		for (AbstractNode child : children)
			toReturn.addAll(child.getLeaves());

		return toReturn;
	}

	public interface ChildCreationListener
	{
		void childCreated(AbstractNode node);
		void childDeleted(AbstractNode node);
	}
	
	public void addChild(AbstractNode child)
	{
		if(children.add(child))
		{
			for(ChildCreationListener listener : childCreationListeners)
				listener.childCreated(child);
			timestampModified();
		}
	}

	public void removeChild(AbstractNode child)
	{
		if (children.remove(child))
		{
			child.deleteDatas();
			for(ChildCreationListener listener : childCreationListeners)
				listener.childDeleted(child);
			timestampModified();
		}
	}

	public void removeAllChildren()
	{
		ArrayList<AbstractNode> savedList = new ArrayList<AbstractNode>(children);
		for (AbstractNode child : new ArrayList<AbstractNode>(children))
			removeChild(child);
	}

	public int numChildren()
	{
		return children.size();
	}

	public void addChildCreationListener(ChildCreationListener listener)
	{
		childCreationListeners.add(listener);
	}
	
	public void removeChildCreationListener(ChildCreationListener listener)
	{
		childCreationListeners.remove(listener);
	}

	@Override
	public void setVisible(boolean visible)
	{
		for (AbstractNode child : children)
			child.setVisible(visible);

		super.setVisible(visible);
	}

	@Override
	public void refresh()
	{
		for (AbstractNode child : children)
			child.refresh();
		if (!isManager())
		{
			lastUpdate = System.nanoTime();
			return;
		}

		if (lastUpdate <= modificationTime)
		{
			refreshData();
			refreshActor();
		}
		else
		{
			for (LeafNode leaf : getLeaves())
			{
				if (lastUpdate <= leaf.modificationTime)
				{
					refreshData();
					refreshActor();
					break;
				}
			}
		}
		
		if (lastUpdate <= selectionTime)
		{
			refreshHighlight();
		}
		else
		{
			for (LeafNode leaf : getLeaves())
			{
				if (lastUpdate <= leaf.selectionTime)
				{
					refreshHighlight();
					break;
				}
			}
		}

		lastUpdate = System.nanoTime();
	}

	private void refreshData()
	{
		if (LOGGER.isLoggable(Level.FINEST))
			LOGGER.finest("Refresh data for "+this);
		// Compute the sizes
		int nodesSize = 0;
		int verticesSize = 0;
		int linesSize = 0;
		int polysSize = 0;
		nbrOfVertices = 0;
		nbrOfLines = 0;
		nbrOfPolys = 0;
		boolean buildNormals = true;

		List<LeafNode> leaves = getLeaves();
		int numberOfLeaves = leaves.size();
		offsetsVertices = new TIntArrayList(numberOfLeaves + 1);
		offsetsLines    = new TIntArrayList(numberOfLeaves + 1);
		offsetsPolys    = new TIntArrayList(numberOfLeaves + 1);

		for (LeafNode leaf : leaves)
		{
			offsetsVertices.add(nbrOfVertices);
			offsetsLines.add(nbrOfLines);
			offsetsPolys.add(nbrOfPolys);

			if (!leaf.isVisible())
				continue;

			LeafNode.DataProvider dataProvider = leaf.getDataProvider();

			dataProvider.load();

			nodesSize += dataProvider.getNodes().length;
			verticesSize += dataProvider.getVertices().length;
			linesSize += dataProvider.getLines().length;
			polysSize += dataProvider.getPolys().length;


			nbrOfVertices += dataProvider.getNbrOfVertices();

			nbrOfLines += dataProvider.getNbrOfLines();

			nbrOfPolys += dataProvider.getNbrOfPolys();

			if (dataProvider.getNormals() == null)
				buildNormals = false;
		}
		offsetsVertices.add(nbrOfVertices);
		offsetsLines.add(nbrOfLines);
		offsetsPolys.add(nbrOfPolys);

		// If there is no nodes then there is no normals
		if (nodesSize == 0)
			buildNormals = false;

		// Compute the arrays
		float[] nodes = new float[nodesSize];
		float[] normals = null;
		if (buildNormals)
			normals = new float[nodesSize];
		int[] vertices = new int[verticesSize];
		int[] lines = new int[linesSize];
		int[] polys = new int[polysSize];
		int offsetNode = 0;
		int offsetVertex = 0;
		int offsetLine = 0;
		int offsetPoly = 0;

		table = new vtkLookupTable();
		table.SetNumberOfTableValues(leaves.size());
		table.SetTableRange(0, leaves.size());

		for (int i = 0; i < numberOfLeaves; ++i)
		{
			LeafNode leaf = leaves.get(i);

			if (!leaf.isVisible())
				continue;

			LeafNode.DataProvider dataProvider = leaf.getDataProvider();

			final int numberOfNode = offsetNode / 3;
			float[] nodesNode = dataProvider.getNodes();
			System.arraycopy(nodesNode, 0, nodes, offsetNode, nodesNode.length);
			if (buildNormals)
			{
				float[] normalsNode = dataProvider.getNormals();
				if (normalsNode == null)
					Arrays.fill(normals, offsetNode, offsetNode + nodesNode.length, 0.f);
				else
					System.arraycopy(normalsNode, 0, normals, offsetNode, normalsNode.length);
			}
			offsetNode += nodesNode.length;



			int[] verticesNode = dataProvider.getVertices();
			System.arraycopy(verticesNode, 0, vertices, offsetVertex, verticesNode.length);

			// Make an offset
			for (int j = offsetVertex; j < offsetVertex + verticesNode.length;)
			{
				vertices[++j] += numberOfNode;
				++j;
			}
			offsetVertex += verticesNode.length;

			int[] linesNode = dataProvider.getLines();
			System.arraycopy(linesNode, 0, lines, offsetLine, linesNode.length);

			// Make an offset
			for (int j = offsetLine; j < offsetLine + linesNode.length;)
			{
				lines[++j] += numberOfNode;
				lines[++j] += numberOfNode;
				++j;
			}
			offsetLine += linesNode.length;

			int[] polysNode = dataProvider.getPolys();
			System.arraycopy(polysNode, 0, polys, offsetPoly, polysNode.length);

			// Make an offset
			for (int j = offsetPoly; j < offsetPoly + polysNode.length;)
			{
				int size = polys[j++];
				for (int c = 0; c < size; ++c)
					polys[j++] += numberOfNode;
			}
			offsetPoly += polysNode.length;

			Color color = leaf.getColor();
			if (LOGGER.isLoggable(Level.FINEST))
				LOGGER.finest("Compound: set color to "+color+" (opacity="+color.getAlpha()+")");
			table.SetTableValue(i, (double) color.getRed() / 255., (double) color.getGreen() / 255., (double) color.getBlue() / 255., (double) color.getAlpha() / 255.);

			dataProvider.unLoad();
		}

		// Compute the id association array
		int[] ids = new int[nbrOfVertices + nbrOfLines + nbrOfPolys];
		for (int leafIndex = 0; leafIndex < numberOfLeaves; ++leafIndex)
		{
			// Vertex part
			int begin = offsetsVertices.get(leafIndex);
			int end = offsetsVertices.get(leafIndex + 1);
			Arrays.fill(ids, begin, end, leafIndex);

			// Line part
			begin = nbrOfVertices + offsetsLines.get(leafIndex);
			end = nbrOfVertices + offsetsLines.get(leafIndex + 1);
			Arrays.fill(ids, begin, end, leafIndex);

			// Poly part
			begin = nbrOfVertices + nbrOfLines + offsetsPolys.get(leafIndex);
			end = nbrOfVertices + nbrOfLines + offsetsPolys.get(leafIndex + 1);
			Arrays.fill(ids, begin, end, leafIndex);
		}

		NodeData nodeData = new NodeData(nodes, normals, nbrOfVertices, vertices, nbrOfLines, lines, nbrOfPolys, polys);

		createData(nodeData);

		vtkIntArray idsNative = new vtkIntArray();
		idsNative.SetJavaArray(ids);
		data.GetCellData().SetScalars(idsNative);
		timestampData();

		if(mapper == null)
			mapper = new vtkPolyDataMapper();
		getMapperCustomiser().customiseMapper(mapper);
		mapper.SetInput(data);
		mapper.Update();		
		mapper.SetLookupTable(table);
		mapper.UseLookupTableScalarRangeOn();
		mapper.SetScalarModeToUseCellData();
	}

	// Must always be called after refreshData
	private void refreshActor()
	{
		if (LOGGER.isLoggable(Level.FINEST))
			LOGGER.finest("Refresh actor for "+this);

		boolean actorCreated = (actor == null);
		if(actorCreated)
			actor = new vtkActor();
		getActorCustomiser().customiseActor(actor);	
		actor.SetMapper(mapper);
		actor.SetVisibility(Utils.booleanToInt(visible));
		actor.SetPickable(Utils.booleanToInt(pickable));
		
		if (actorCreated)
		{
			fireActorCreated(actor);
			if (LOGGER.isLoggable(Level.FINEST))
				LOGGER.log(Level.FINEST, "New actor created:  "+actor);
		}
	}

	@Override
	protected void deleteDatas()
	{
		super.deleteDatas();
		offsetsVertices = null;
		offsetsLines = null;
		offsetsPolys = null;
		table = null;
		for(AbstractNode n : children)
			n.deleteDatas();
	}

	private int getNbrOfCells()
	{
		return nbrOfVertices + nbrOfLines + nbrOfPolys;
	}

	private void refreshHighlight()
	{
		if (LOGGER.isLoggable(Level.FINEST))
			LOGGER.log(Level.FINEST, "Refresh highlight for "+this);


		if (selected)
		{
			// The whole actor is selected, so display it
			// as highlighted.
			mapper.ScalarVisibilityOff();
			getActorSelectionCustomiser().customiseActorSelection(actor);
			getMapperSelectionCustomiser().customiseMapperSelection(mapper);

			deleteSelectionHighlighter();
		}
		else
		{
			// Reset original actor colors
			mapper.ScalarVisibilityOn();
			getActorCustomiser().customiseActor(actor);
			getMapperCustomiser().customiseMapper(mapper);

			refreshSelectionHighlighter();
		}
	}

	private void refreshSelectionHighlighter()
	{
		TIntArrayList selection = new TIntArrayList(getNbrOfCells());
		int leafIndex = -1;
		for (LeafNode leaf : getLeaves())
		{
			leafIndex++;
			if (leaf.selected)
			{
				// If a node is selected, select all cells

				// Vertices
				int vBegin = offsetsVertices.get(leafIndex);
				int vEnd = offsetsVertices.get(leafIndex + 1);
	
				// Lines
				int lBegin = offsetsLines.get(leafIndex) + nbrOfVertices;
				int lEnd = offsetsLines.get(leafIndex + 1) + nbrOfVertices;
	
				// Polys
				int pBegin = offsetsPolys.get(leafIndex) + nbrOfVertices + nbrOfLines;
				int pEnd = offsetsPolys.get(leafIndex + 1) + nbrOfVertices + nbrOfLines;
				selection.ensureCapacity(selection.size() +
					(vEnd + 1 - vBegin) +
					(lEnd + 1 - lBegin) +
					(pEnd + 1 - pBegin));
	
				// Add vertices
				for (int j = vBegin; j < vEnd; ++j)
					selection.add(j);
				// Add lines
				for (int j = lBegin; j < lEnd; ++j)
					selection.add(j);
				// Add polys
				for (int j = pBegin; j < pEnd; ++j)
					selection.add(j);
			}
			else if (leaf.hasCellSelection())
			{
				int[] cellSelection = leaf.getCellSelection();
				selection.ensureCapacity(selection.size()+cellSelection.length);
				for (int j = 0; j < cellSelection.length; ++j)
					selection.add(leafIndexToNodeIndex(leaf, leafIndex, cellSelection[j]));
			}
		}

		if (selection.isEmpty())
		{
			deleteSelectionHighlighter();
			return;
		}

		boolean actorCreated = (selectionHighlighter == null);
		if (actorCreated)
		{
			selectionHighlighter = new vtkActor();
			selectionHighlighter.PickableOff();
		}
		getActorSelectionCustomiser().customiseActorSelection(selectionHighlighter);

		selectionHighlighterMapper = new vtkPolyDataMapper();
		selectionHighlighterMapper.ScalarVisibilityOff();
		selectionHighlighterMapper.SetInput(selectInto(data, selection.toNativeArray()));
		selectionHighlighter.SetMapper(selectionHighlighterMapper);
		getMapperSelectionCustomiser().customiseMapperSelection(selectionHighlighterMapper);
		
		if (actorCreated)
			fireActorCreated(selectionHighlighter);
	}

	private final int nodeIndexToLeafIndex(int leaf, int index)
	{
		if (0 <= index && index < nbrOfVertices)
			return index - offsetsVertices.getQuick(leaf);

		index -= nbrOfVertices;
		if (0 <= index && index < nbrOfLines)
			return index - offsetsLines.getQuick(leaf);

		index -= nbrOfLines;
		if (0 <= index && index < nbrOfPolys)
			return index - offsetsPolys.getQuick(leaf);

		throw new IllegalArgumentException("Wrong index: "+index);
	}

	private final int leafIndexToNodeIndex(LeafNode leaf, int leafIndex, int index)
	{
		LeafNode.DataProvider leafDataProvider = leaf.getDataProvider();
		int numberOfVerticesLeaf = leafDataProvider.getNbrOfVertices();
		int numberOfLinesLeaf = leafDataProvider.getNbrOfLines();
		int numberOfPolysLeaf = leafDataProvider.getNbrOfPolys();

		if (0 <= index && index < numberOfVerticesLeaf)
			return index + offsetsVertices.getQuick(leafIndex);

		index -= numberOfVerticesLeaf;

		if (0 <= index && index < numberOfLinesLeaf)
			return index + nbrOfVertices + offsetsLines.getQuick(leafIndex);

		index -= numberOfLinesLeaf;
		if (0 <= index && index < numberOfPolysLeaf)
			return index + nbrOfVertices + nbrOfLines + offsetsPolys.getQuick(leafIndex);

		throw new IllegalArgumentException("Wrong index: "+index);
	}

	private vtkPolyData selectInto(vtkPolyData input, int[] cellID)
	{
		vtkSelection sel = new vtkSelection();
		//sel.ReleaseDataFlagOn();
		sel.GetProperties().Set(sel.CONTENT_TYPE(), 4); // 4 MEANS INDICES (see the enumeration)

		sel.GetProperties().Set(sel.FIELD_TYPE(), 0); // 0 MEANS CELLS

		// list of cells to be selected
		vtkIdTypeArray arr = Utils.setValues(cellID);
		sel.SetSelectionList(arr);

		vtkExtractSelectedPolyDataIds selFilter = new vtkExtractSelectedPolyDataIds();
		selFilter.ReleaseDataFlagOn();
		selFilter.SetInput(1, sel);
		selFilter.SetInput(0, input);

		vtkPolyData dataFiltered = selFilter.GetOutput();
		selFilter.Update();

		return dataFiltered;
	}

	void setCellSelection(PickContext pickContext, int [] cellSelection)
	{
		if (!isManager())
			throw new RuntimeException("The Node has to be a manager to manage the selection");

		int[] ids = ((vtkIntArray) data.GetCellData().GetScalars()).GetJavaArray();

		List<LeafNode> leaves = getLeaves();
		for (LeafNode leaf : leaves)
			leaf.clearCellSelection();

		TIntArrayList [] selectionChildren = new TIntArrayList[leaves.size()];
		for (int i = 0; i < leaves.size(); ++i)
			selectionChildren[i] = new TIntArrayList();
		// Compute the selections		
		for (int cellID : cellSelection)
		{
			int nodeID = ids[cellID];
			selectionChildren[nodeID].add(nodeIndexToLeafIndex(nodeID, cellID));
		}

		// Send the selections to the children
		for (int i = 0; i < leaves.size(); ++i)
		{
			if (!selectionChildren[i].isEmpty())
				leaves.get(i).setCellSelection(pickContext, selectionChildren[i].toNativeArray());
		}
		
		timestampSelected();
	}
	
	public void clearCellSelection()
	{
		for (LeafNode leaf : getLeaves())
			leaf.clearCellSelection();
		
		timestampSelected();
	}

}
