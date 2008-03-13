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
 * (C) Copyright 2004, by EADS CRC
 */

package org.jcae.netbeans.viewer3d;

import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import org.jcae.netbeans.NodeSelectionManager;
import org.jcae.viewer3d.View;
import org.jcae.viewer3d.Viewable;
import org.openide.util.actions.SystemAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * @author Jerome Robert
 *
 */
public class View3D extends TopComponent
{	
	protected View canvas;
	public View3D()
	{
		View3DManager.getDefault().counter++;		
		this.canvas=new View(WindowManager.getDefault().getMainWindow());
		setLayout(new BorderLayout());
		add(canvas, BorderLayout.CENTER);
		canvas.setOriginAxisVisible(true);
	}
	
	@Override
	public int getPersistenceType()
	{
		return TopComponent.PERSISTENCE_NEVER;
	}
	
	public View getView()
	{
		return canvas;
	}

	public void remove(Viewable viewable)
	{
		canvas.remove(viewable);
		if(viewable instanceof PropertyChangeListener)
			NodeSelectionManager.getDefault().removePropertyChangeListener(
				(PropertyChangeListener)viewable);
	}
        
	@Override
	protected void componentClosed()
	{
		canvas.setVisible(false);
		;
		if(View3DManager.getDefault().activeView==this)
			View3DManager.getDefault().activeView=null;
	    super.componentClosed();
	}
	
	/* (non-Javadoc)
	 * @see org.openide.windows.TopComponent#componentActivated()
	 */
	@Override
	protected void componentActivated()
	{		
		View3DManager.getDefault().activeView=this;
	}
	
	@Override
	public String toString()
	{
	    return this.getName();
	}

	/**
	 * Add a viewable to this view and make it current. If viewable implements
	 * PropertyChangeListener it will be notified when the global node selection
	 * changes.
	 */
	public void add(Viewable viewable)
	{
		getView().add(viewable);
		getView().setCurrentViewable(viewable);
		if(viewable instanceof PropertyChangeListener)
			NodeSelectionManager.getDefault().addPropertyChangeListener(
				(PropertyChangeListener)viewable);		
		SystemAction.get(SelectViewableAction.class).refresh();
	}
}
