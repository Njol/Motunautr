/*
This file is part of Motunautr, an alternative to the default Windows desktop and start menu.
Copyright (C) 2017 Peter Güttinger

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package ch.njol.betterdesktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.File;

import javax.swing.JDialog;

public class BDDropdown extends JDialog {
	private static final long serialVersionUID = -3839411082571168815L;
	
	private final Window parent;
	private final BDFileContainer files;
	
	public BDDropdown(final BDWindow bdWindow, final Window parent, final File folder) {
		this.parent = parent;
		
		setType(Type.POPUP);
		setUndecorated(true);
		
		getContentPane().setBackground(null);
		setBackground(new Color(0, 0, 0, 200));
//		setOpacity(Settings.opacity);
		
		add(files = new BDFileContainer(bdWindow, folder, false), BorderLayout.CENTER);
		files.setBackground(new Color(0, 0, 0));
		final int n = files.numFiles();
		final int width = n <= 3 ? 1 : n <= 9 ? 2 : 3;
		files.setPreferredSize(new Dimension(width * (FileIcon.SIZE_X + BDFileContainer.GAP_X) + BDFileContainer.GAP_X,
				Math.max(1, 1 + (n - 1) / width) * (FileIcon.SIZE_Y + BDFileContainer.GAP_Y) + BDFileContainer.GAP_Y));
		
		setAutoRequestFocus(true);
		addWindowFocusListener(new WindowFocusListener() {
			@Override
			public void windowLostFocus(final WindowEvent e) {
				// don't close a dropdown if a descendant is visible
				if (e.getOppositeWindow() instanceof BDDropdown && ((BDDropdown) e.getOppositeWindow()).hasParent(BDDropdown.this))
					return;
				dispose();
				Window p = parent;
				while (p instanceof BDDropdown) {
					if (p.isFocused() || p == e.getOppositeWindow())
						break;
					p.dispose();
					p = ((BDDropdown) p).parent;
				}
			}
			
			@Override
			public void windowGainedFocus(final WindowEvent e) {}
		});
		
	}
	
	@Override
	public String toString() {
		return "BDDropdown[" + files.folder + "]";
	}
	
	protected boolean hasParent(final BDDropdown dropdown) {
		if (parent == dropdown)
			return true;
		if (parent instanceof BDDropdown)
			return ((BDDropdown) parent).hasParent(dropdown);
		return false;
	}
	
	public void showFor(final FileIcon icon, final boolean downwards) {
		
		pack();
		
		final Point l = icon.getLocationOnScreen();
		final int x = l.x - (getWidth() - icon.getWidth()) / 2;
		final int y = l.y + FileIcon.SIZE_Y;
		
		setLocation(x, y);
		setLocation(Utils.insert(getBounds(), getGraphicsConfiguration().getBounds()));
		setVisible(true);
		requestFocus();
		
	}
	
}
