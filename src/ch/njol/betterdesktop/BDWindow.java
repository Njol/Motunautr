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
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

// this is a JDialog so that it is focusable (to be able to easily close dropdown menus, and to hide the taskbar when clicked) but does not appear in the taskbar
public class BDWindow extends JDialog {
	
	public File folder;
	public File metaFolder;
	private File propFile;
	
	private static class Props extends PropertiesEx {
		int x, y, numFilesX = 4;
	}
	
	private final Props props = new Props();
	
	private final JLabel title;
	private final static int TITLE_HEIGHT = 20;
	
	public final BDFileContainer files;
	
	private final JComponent resizeArea;
	private final static int RESIZE_AREA_WIDTH = 10;
	
	public BDWindow(final File folder) {
		this.folder = folder;
		
		setLayout(null);
		setUndecorated(true);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		
		// allows to drag this window around by clicking anywhere inside it, and prevents moving it outside the screen.
		// also allows resizing by dragging the bottom right corner.
		final FixedMouseAdapter ma = new FixedMouseAdapter() {
			private int startX, startY;
			private boolean isDrag;
			
			@Override
			public void mousePressed(final MouseEvent e) {
				if (e.getButton() != 1)
					return;
				startX = e.getX();
				startY = e.getY();
				isDrag = true;
			}
			
			@Override
			public void mouseDragged(final MouseEvent e) {
				if (isDrag) {
					final Rectangle wb = getBounds();
					wb.x += e.getX() - startX;
					wb.y += e.getY() - startY;
					final Point p = Utils.insert(wb, getGraphicsConfiguration().getBounds());
					// just like above
					if (Math.abs(p.x - getX()) <= 2)
						p.x = getX();
					if (Math.abs(p.y - getY()) <= 2)
						p.y = getY();
					setLocation(p);
				}
			}
			
			@Override
			public void mouseReleased(final MouseEvent e) {
				if (title.getBounds().contains(e.getPoint())) {
					if (e.getClickCount() == 2 && e.getButton() == 1) {
						// double click on title opens folder (here to be able to drag the window at the title too)
						try {
							Desktop.getDesktop().open(files.folder);
						} catch (final IOException e1) {
							e1.printStackTrace();
						}
					} else if (e.getButton() == 2) {
						// middle click allows renaming just like icons themselves
						startRename();
					}
				}
				if (isDrag && e.getButton() == 1) {
					setLocation(Utils.insert(getBounds(), getGraphicsConfiguration().getBounds()));
					saveLocation();
					isDrag = false;
				}
			}
			
			@Override
			public void mouseEntered(final MouseEvent e) {
				repaint(); // sometimes the window becomes solid white if a component is redrawn, this should work around that problem
			}
		};
		ma.addToComponent(this);
		
		Settings.INSTANCE.mainWindowOpacity.addListener(val -> {
			if (SwingUtilities.isEventDispatchThread()) {
				setBackground(new Color(0, 0, 0, val));
			} else {
				SwingUtilities.invokeLater(() -> {
					setBackground(new Color(0, 0, 0, val));
				});
			}
		});
		getContentPane().setBackground(new Color(0, 0, 0, 0));
		
		add(title = new JLabel(folder.getName()));
		title.setBackground(null);
		title.setForeground(new Color(255, 255, 255));
		title.setHorizontalAlignment(SwingConstants.CENTER);
		title.setVerticalAlignment(SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		
		add(resizeArea = new JComponent() {
			@Override
			protected void paintComponent(final Graphics g) {
				g.setColor(new Color(0, 0, 0, 100));
				g.drawLine(getWidth() - 3, getHeight() / 2 - 7, getWidth() - 3, getHeight() / 2 + 7);
				g.drawLine(getWidth() - 5, getHeight() / 2 - 7, getWidth() - 5, getHeight() / 2 + 7);
			}
		}, 0);
		resizeArea.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
		final MouseAdapter resizeListener = new MouseAdapter() {
			private int startX;
			private boolean isResize;
			
			@Override
			public void mousePressed(final MouseEvent e) {
				if (e.getButton() != 1)
					return;
				startX = e.getX();
				isResize = true;
			}
			
			@Override
			public void mouseDragged(final MouseEvent e) {
				if (isResize) {
					final int desiredWidth = resizeArea.getX() + e.getX() - startX;
					setNumFilesX((int) Math.round(1.0 * (desiredWidth - BDFileContainer.GAP_X) / (FileIcon.SIZE_X + BDFileContainer.GAP_X)), true);
				}
			}
			
			@Override
			public void mouseReleased(final MouseEvent e) {
				if (isResize && e.getButton() == 1) {
					setLocation(Utils.insert(getBounds(), getGraphicsConfiguration().getBounds()));
					saveLocation();
					isResize = false;
				}
			}
			
			@Override
			public void mouseEntered(final MouseEvent e) {
				BDWindow.this.repaint(); // same as above
			}
		};
		resizeArea.addMouseMotionListener(resizeListener);
		resizeArea.addMouseListener(resizeListener);
		
		metaFolder = new File(folder, ".motunautr");
		propFile = new File(metaFolder, "settings.properties");
		
		title.setText(folder.getName());
		
		add(files = new BDFileContainer(this, folder, true, 200));
		resizeArea.setVisible(files.numFiles() > 1);
		
		if (propFile.exists()) {
			try (Reader r = new InputStreamReader(new FileInputStream(propFile), StandardCharsets.UTF_8)) {
				props.load(r);
				setNumFilesX(props.numFilesX, false);
				setLocation(props.x, props.y);
				setLocation(Utils.insert(getBounds(), getGraphicsConfiguration().getBounds()));
			} catch (final IOException e) {
				e.printStackTrace();
			}
		} else {
			setNumFilesX(props.numFilesX, false);
			setLocation(Utils.insert(getBounds(), getGraphicsConfiguration().getBounds()));
			saveLocation(); // create properties file
		}
		
		metaFolder.mkdir();
		try {
			Files.setAttribute(metaFolder.toPath(), "dos:hidden", true);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		
//		setDropTarget(new DropTa);
//		setTransferHandler(new TransferHandler(null) {
//
//		});
		
		Main.watchDirectory(folder.toPath(), new FileWatcher.DirectoryListener(200) {
			@Override
			public boolean ignoreChange(final Path path) {
				return path.equals(folder.toPath()) || path.startsWith(metaFolder.toPath().toAbsolutePath());
			}
			
			@Override
			public void directoryChanged() {
				System.out.println("Folder " + files.folder + " changed, reloading window");
				SwingUtilities.invokeLater(() -> {
					files.reload();
				});
			}
		});
		
	}
	
	public void setNumFilesX(int numFilesX, final boolean suppressFlickering) {
		if (numFilesX > files.numFiles())
			numFilesX = files.numFiles();
		if (numFilesX < 1)
			numFilesX = 1;
		props.numFilesX = numFilesX;
		final int numFilesY = Math.max(1, 1 + (files.numFiles() - 1) / numFilesX);
		
		int sizeX = numFilesX * (FileIcon.SIZE_X + BDFileContainer.GAP_X) + BDFileContainer.GAP_X,
				sizeY = TITLE_HEIGHT + numFilesY * (FileIcon.SIZE_Y + BDFileContainer.GAP_Y) + BDFileContainer.GAP_Y;
		
		if (suppressFlickering) {
			// the following checks prevent flickering when resizing (most likely due to high DPI effects)
			if (Math.abs(sizeX - getWidth()) <= 2)
				sizeX = getWidth();
			if (Math.abs(sizeY - getHeight()) <= 2)
				sizeY = getHeight();
		}
		
		setSize(sizeX, sizeY);
		
		// blocky, and blurred area is still rectangular
//		setShape(new RoundRectangle2D.Double(0, 0, sizeX, sizeY, 10, 10));
	}
	
	public void saveLocation() {
		props.x = getX();
		props.y = getY();
		try (Writer w = new OutputStreamWriter(new FileOutputStream(propFile), StandardCharsets.UTF_8)) {
			props.save(w);
		} catch (final IOException ex) {
			ex.printStackTrace();
		}
	}
	
	@Override
	public Dimension getPreferredSize() {
		return getSize();
	}
	
	@Override
	public void pack() {
		super.pack();
		doLayout();
	}
	
	@Override
	public void doLayout() {
		title.setBounds(0, 2, getWidth(), TITLE_HEIGHT);
		// 'files' has some padding already
		files.setBounds(0, TITLE_HEIGHT, getWidth(), getHeight() - TITLE_HEIGHT);
		resizeArea.setBounds(getWidth() - RESIZE_AREA_WIDTH, 0, RESIZE_AREA_WIDTH, getHeight());
		super.doLayout(); // this is required for some reason
	}
	
	private void startRename() {
		final String name = files.folder.getName();
		final String newName = (String) JOptionPane.showInputDialog(null, "", "Rename", JOptionPane.PLAIN_MESSAGE, null, null, name);
		if (newName != null) {
			final File newFile = new File(files.folder.getParentFile(), newName);
			files.folder.renameTo(newFile);
		}
	}
	
}
