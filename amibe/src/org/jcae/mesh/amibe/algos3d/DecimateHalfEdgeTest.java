/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finite element mesher, Plugin architecture.

    Copyright (C) 2007, by EADS France

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.jcae.mesh.amibe.algos3d;

import org.jcae.mesh.amibe.ds.Mesh;
import org.jcae.mesh.amibe.ds.Triangle;
import org.jcae.mesh.amibe.ds.Vertex;
import java.util.Map;
import java.util.HashMap;
import static org.junit.Assert.*;
import org.junit.Test;

public class DecimateHalfEdgeTest
{
	private Mesh mesh;
	private Vertex [] v;
	private Triangle [] T;
	private int vertexLabel = 0;

	// m Vertex on rows, n Vertex on columns
	private void createMxNShell(int m, int n)
	{
		/*   v3       v4        v5 
		 *   +---------+---------+
		 *   | \       | \       |
		 *   |   \  T1 |   \  T3 |
		 *   |     \   |     \   |
		 *   |  T0   \ |  T2   \ |
		 *   +---------+---------+
		 *   v0        v1       v2
		 */
		v = new Vertex[m*n];
		for (int j = 0; j < n; j++)
			for (int i = 0; i < m; i++)
				v[m*j+i] = (Vertex) mesh.createVertex(i, j, 0.0);
		for (int i = 0; i < v.length; i++)
			v[i].setLabel(i);
		vertexLabel = v.length;
		T = createMxNTriangles(m, n, v);
	}

	private Triangle [] createMxNTriangles(int m, int n, Vertex [] vv)
	{
		Triangle [] tt = new Triangle[2*(m-1)*(n-1)];
		for (int j = 0; j < n-1; j++)
		{
			for (int i = 0; i < m-1; i++)
			{
				tt[2*(m-1)*j+2*i]   = (Triangle) mesh.createTriangle(vv[m*j+i], vv[m*j+i+1], vv[m*(j+1)+i]);
				tt[2*(m-1)*j+2*i+1] = (Triangle) mesh.createTriangle(vv[m*j+i+1], vv[m*(j+1)+i+1], vv[m*(j+1)+i]);
				vv[m*j+i].setLink(tt[2*(m-1)*j+2*i]);
			}
			vv[m*j+m-1].setLink(tt[2*(m-1)*j+2*m-3]);
		}
		// Last row
		for (int i = 0; i < m-1; i++)
			vv[m*(n-1)+i].setLink(tt[2*(m-1)*(n-2)+2*i]);
		vv[m*n-1].setLink(tt[2*(m-1)*(n-1)-1]);
		int cnt = mesh.getTriangles().size();
		for (Triangle t: tt)
		{
			mesh.add(t);
			t.setGroupId(cnt);
			cnt++;
		}
		return tt;
	}
	
	private void rotateMxNShellAroundY(int m, int n, double angle)
	{
		// Create new vertices and append them to current mesh
		assert v.length == m*n;
		Vertex [] vy = new Vertex[v.length];
		vertexLabel = v.length;
		double ct = Math.cos(angle*Math.PI / 180.0);
		double st = Math.sin(angle*Math.PI / 180.0);
		for (int i = 0; i < v.length; i++)
		{
			if (i%m == 0)
				vy[i]   = v[i];
			else
			{
				double [] xyz = v[i].getUV();
				vy[i]   = (Vertex) mesh.createVertex(ct*xyz[0]+st*xyz[2], xyz[1], -st*xyz[0]+ct*xyz[2]);
				vy[i].setLabel(vertexLabel);
				vertexLabel++;
			}
		}
		createMxNTriangles(m, n, vy);
	}
	
	private void testShell(int m, int n)
	{
		final Map<String, String> options = new HashMap<String, String>();
		options.put("size", "0.1");
		mesh = new Mesh();
		createMxNShell(m, n);
		mesh.buildAdjacency();
		assertTrue("Mesh is not valid", mesh.isValid());
		int expected = 2;
		int res = Integer.MAX_VALUE;
		while (res != expected)
		{
			new DecimateHalfEdge(mesh, options).compute();
			assertTrue("Mesh is not valid", mesh.isValid());
			int newres = DecimateHalfEdge.countInnerTriangles(mesh);
			if (newres >= res)
				break;
			res = newres;
		}
		assertTrue("Final number of triangles: "+res, res == expected);
		assertTrue("Mesh is not valid", mesh.isValid());
	}

	private void testCross(int m, int n)
	{
		final Map<String, String> options = new HashMap<String, String>();
		options.put("size", "0.1");
		mesh = new Mesh();
		createMxNShell(m, n);
		rotateMxNShellAroundY(m, n, 90);
		rotateMxNShellAroundY(m, n, 180);
		rotateMxNShellAroundY(m, n, 270);
		mesh.buildAdjacency();
		assertTrue("Mesh is not valid", mesh.isValid());
		int expected = 8;
		int res = Integer.MAX_VALUE;
		while (res != expected)
		{
			new DecimateHalfEdge(mesh, options).compute();
			assertTrue("Mesh is not valid", mesh.isValid());
			int newres = DecimateHalfEdge.countInnerTriangles(mesh);
			if (newres >= res)
				break;
			res = newres;
		}
		assertTrue("Final number of triangles: "+res, res == expected);
	}

	@Test public void testNonManifold()
	{
		final Map<String, String> options = new HashMap<String, String>();
		options.put("size", "0.1");
		mesh = new Mesh();
		//  v3         v4        v5
		//   +---------+---------+
		//   | \       |       / |
		//   |   \  T2 | T5  /   |
		//   |     \   |   /     |
		//   |       \ | /       |
		//   |  T1     + v6  T4  |
		//   |       / | \       |
		//   |     /   |   \     |
		//   |   /     |     \   |
		//   | /   T0  |  T3   \ |
		//   +---------+---------+
		//  v0         v1        v2
		v = new Vertex[11];
		v[0] = (Vertex) mesh.createVertex(-1.0, 0.0, 0.0);
		v[1] = (Vertex) mesh.createVertex(0.0, 0.0, 0.0);
		v[2] = (Vertex) mesh.createVertex(1.0, 0.0, 0.0);
		v[3] = (Vertex) mesh.createVertex(-1.0, 2.0, 0.0);
		v[4] = (Vertex) mesh.createVertex(0.0, 2.0, 0.0);
		v[5] = (Vertex) mesh.createVertex(1.0, 2.0, 0.0);
		v[6] = (Vertex) mesh.createVertex(0.0, 1.0, 0.0);
		v[7] = (Vertex) mesh.createVertex(0.0, 0.0, 1.0);
		v[8] = (Vertex) mesh.createVertex(0.0, 2.0, 1.0);
		v[9] = (Vertex) mesh.createVertex(0.0, 0.0, -1.0);
		v[10] = (Vertex) mesh.createVertex(0.0, 2.0, -1.0);
		for (int i = 0; i < v.length; i++)
			v[i].setLabel(i);

		T = new Triangle[12];
		T[0] = (Triangle) mesh.createTriangle(v[0], v[1], v[6]);
		T[1] = (Triangle) mesh.createTriangle(v[6], v[3], v[0]);
		T[2] = (Triangle) mesh.createTriangle(v[3], v[6], v[4]);
		T[3] = (Triangle) mesh.createTriangle(v[2], v[1], v[6]);
		T[4] = (Triangle) mesh.createTriangle(v[5], v[2], v[6]);
		T[5] = (Triangle) mesh.createTriangle(v[5], v[6], v[4]);
		T[6] = (Triangle) mesh.createTriangle(v[7], v[1], v[6]);
		T[7] = (Triangle) mesh.createTriangle(v[6], v[4], v[8]);
		T[8] = (Triangle) mesh.createTriangle(v[7], v[6], v[8]);
		T[9] = (Triangle) mesh.createTriangle(v[9], v[1], v[6]);
		T[10] = (Triangle) mesh.createTriangle(v[10], v[9], v[6]);
		T[11] = (Triangle) mesh.createTriangle(v[10], v[6], v[4]);
		v[0].setLink(T[0]);
		v[1].setLink(T[0]);
		v[2].setLink(T[3]);
		v[3].setLink(T[1]);
		v[4].setLink(T[5]);
		v[5].setLink(T[4]);
		v[6].setLink(T[4]);
		v[7].setLink(T[8]);
		v[8].setLink(T[8]);
		v[9].setLink(T[10]);
		v[10].setLink(T[10]);
		int cnt = 0;
		for (Triangle t: T)
		{
			if (t == null)
				continue;
			mesh.add(t);
			t.setGroupId(cnt);
			cnt++;
		}
		mesh.buildAdjacency();
		assertTrue("Mesh is not valid", mesh.isValid());
		new DecimateHalfEdge(mesh, options).compute();
		int res = DecimateHalfEdge.countInnerTriangles(mesh);
		int expected = 8;
		assertTrue("Final number of triangles: "+res, res == expected);
		assertTrue("Mesh is not valid", mesh.isValid());
	}

	@Test public void testSquare()
	{
		final Map<String, String> options = new HashMap<String, String>();
		options.put("size", "0.1");
		mesh = new Mesh();
		//  v2         v3
		//   +---------+
		//   | \       |
		//   |   \  T1 |
		//   |     \   |
		//   |  T0   \ |
		//   +---------+
		//  v0         v1
		v = new Vertex[4];
		v[0] = (Vertex) mesh.createVertex(0.0, 0.0, 0.0);
		v[1] = (Vertex) mesh.createVertex(1.0, 0.0, 0.0);
		v[2] = (Vertex) mesh.createVertex(0.0, 1.0, 0.0);
		v[3] = (Vertex) mesh.createVertex(1.0, 1.0, 0.0);
		for (int i = 0; i < v.length; i++)
			v[i].setLabel(i);

		T = new Triangle[2];
		T[0] = (Triangle) mesh.createTriangle(v[0], v[1], v[2]);
		T[1] = (Triangle) mesh.createTriangle(v[3], v[2], v[1]);
		v[0].setLink(T[0]);
		v[1].setLink(T[0]);
		v[2].setLink(T[0]);
		v[3].setLink(T[1]);
		int cnt = 0;
		for (Triangle t: T)
		{
			mesh.add(t);
			t.setGroupId(cnt);
			cnt++;
		}
		mesh.buildAdjacency();
		assertTrue("Mesh is not valid", mesh.isValid());
		new DecimateHalfEdge(mesh, options).compute();
		int res = DecimateHalfEdge.countInnerTriangles(mesh);
		int expected = 2;
		assertTrue("Final number of triangles: "+res, res == expected);
		assertTrue("Mesh is not valid", mesh.isValid());
	}

	@Test public void testShell3()
	{
		testShell(3, 3);
	}

	@Test public void testShellLarge()
	{
		testShell(30, 30);
	}

	@Test public void testShellNM1()
	{
		testCross(3, 2);
	}

	@Test public void testShellNM2()
	{
		testCross(3, 3);
	}

	@Test public void testShellNM3()
	{
		testCross(3, 4);
	}

	@Test public void testShellNMLarge()
	{
		testCross(10, 10);
	}

	@Test public void testShellNM3Inverted()
	{
		final Map<String, String> options = new HashMap<String, String>();
		options.put("size", "0.1");
		mesh = new Mesh();
		int m = 3;
		int n = 4;
		createMxNShell(m, n);
		rotateMxNShellAroundY(m, n, 90);
		rotateMxNShellAroundY(m, n, 180);
		rotateMxNShellAroundY(m, n, 270);
		// Invert triangles at the right
		for (int j = 0; j < n-1; j++)
		{
			for (int i = 0; i < m-1; i++)
			{
				Vertex temp = T[2*(m-1)*j+2*i].vertex[1];
				T[2*(m-1)*j+2*i].vertex[1] = T[2*(m-1)*j+2*i].vertex[2];
				T[2*(m-1)*j+2*i].vertex[2] = temp;
				temp = T[2*(m-1)*j+2*i+1].vertex[1];
				T[2*(m-1)*j+2*i+1].vertex[1] = T[2*(m-1)*j+2*i+1].vertex[2];
				T[2*(m-1)*j+2*i+1].vertex[2] = temp;
			}
		}
		mesh.buildAdjacency();
		assertTrue("Mesh is not valid", mesh.isValid());
		int res = Integer.MAX_VALUE;
		int expected = 8;
		while (res != expected)
		{
			new DecimateHalfEdge(mesh, options).compute();
			assertTrue("Mesh is not valid", mesh.isValid());
			int newres = DecimateHalfEdge.countInnerTriangles(mesh);
			if (newres >= res)
				break;
			res = newres;
		}
		assertTrue("Mesh is not valid", mesh.isValid());
		assertTrue("Final number of triangles: "+res, res == expected);
	}

}