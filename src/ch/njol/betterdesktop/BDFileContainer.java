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

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.eclipse.jdt.annotation.Nullable;

public class BDFileContainer extends JPanel {
	
	private final BDWindow window;
	
	public File folder;
	
	public final static int GAP_X = 5, GAP_Y = 5;
	
	private final boolean useFolder;
	
	private @Nullable BDFileContainerListener listener;
	
	private final int maxFiles;
	
	public BDFileContainer(final BDWindow window, final File folder, final boolean useFolder, final int maxFiles) {
		this.window = window;
		this.folder = folder;
		this.useFolder = useFolder;
		this.maxFiles = maxFiles;
		
		setLayout(new FlowLayout(FlowLayout.CENTER, GAP_X, GAP_Y));
		
		setBackground(null);
		setOpaque(false);
		
		reload();
	}
	
	public void setListener(final BDFileContainerListener listener) {
		this.listener = listener;
	}
	
	static interface BDFileContainerListener {
		void onReload();
	}
	
	public void reload() {
		removeAll();
		
		final File[] contents = folder.listFiles();
		if (contents != null) {
			Arrays.sort(contents);
			for (int i = 0; i < contents.length; i++) {
				final File f = contents[i];
				if (f.isHidden() || f.getName().startsWith("."))
					continue;
				add(new FileIcon(window, f, useFolder));
				if (i + 1 == maxFiles && i != contents.length - 1) {
					final JLabel dots = new JLabel("...");
					add(dots);
					dots.setForeground(Color.white);
					dots.setBackground(null);
					dots.setFont(dots.getFont().deriveFont(Font.BOLD, 20f));
					dots.setMinimumSize(new Dimension(FileIcon.SIZE_X, FileIcon.SIZE_Y));
					dots.setPreferredSize(dots.getMinimumSize());
					dots.setVerticalAlignment(SwingConstants.CENTER);
					dots.setHorizontalAlignment(SwingConstants.CENTER);
					dots.setToolTipText("Too many files; click to open folder in explorer.");
					dots.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseClicked(final MouseEvent e) {
							if (e.getButton() == 1) {
								try {
									Desktop.getDesktop().open(folder);
								} catch (final IOException e1) {
									e1.printStackTrace();
								}
							}
						}
					});
					break;
				}
			}
		}
		
		revalidate();
		
		if (listener != null)
			listener.onReload();
	}
	
	public int numFiles() {
		return getComponentCount();
	}
	
	public void rename(final File newFile) {
		if (folder.renameTo(newFile)) {
			folder = newFile;
			if (listener != null)
				listener.onReload();
		}
	}
	
}
