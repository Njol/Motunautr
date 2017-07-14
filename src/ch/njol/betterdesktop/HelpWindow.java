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

import javax.swing.JFrame;
import javax.swing.JTextPane;

public class HelpWindow extends JFrame {
	
	private final static HelpWindow INSTANCE = new HelpWindow();
	
	public HelpWindow() {
		super(Main.NAME + ": Help");
		setIconImage(Main.icon);
		final JTextPane text = new JTextPane();
		text.setText("Basic Configuration:\n"
				+ "Create a folder where you will put the shortcuts to display and set it in the settings\n"
				+ "(it should not be the default Desktop folder, as then icons would be displayed twice).\n"
				+ "Fill that folder with subfolders - these will be the different icon groups.\n"
				+ "In each subfolder, place shortcuts to display, or make subfolders if you want to display not only a shortcut,\n"
				+ "but some alternative shortcuts or related data too. This subfolder can be expanded with a right click on the icon later.\n"
				+ "\n"
				+ "Controls:\n"
				+ "Left click on icon: start application or open file\n"
				+ "Right click on icon: show sub-icons (if any)\n"
				+ "Double click on group title: open folder in explorer\n"
				+ "Middle click on icon or group title: rename file or folder");
		text.setEditable(false);
		add(text);
		pack();
	}
	
	public final static void doShow() {
		INSTANCE.setVisible(true);
	}
	
	@Override
	public void setVisible(final boolean b) {
		if (!b) {
			super.setVisible(false);
			return;
		}
		if (!isVisible()) {
			setLocationRelativeTo(null);
			super.setVisible(true);
		} else {
			toFront();
		}
		requestFocus();
	}
}
