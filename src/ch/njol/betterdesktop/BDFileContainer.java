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
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import org.eclipse.jdt.annotation.Nullable;

public class BDFileContainer extends JPanel {
	
	public final BDWindow window;
	
	public File folder;
	
	public final static int GAP_X = 5, GAP_Y = 5;
	
	private final boolean useFolder;
	
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
	
	public final TransferHandler transferHandler = new TransferHandler(null) {
		@Override
		public int getSourceActions(final JComponent c) {
			// allow dragging icons out of groups
			return c instanceof FileIcon ? COPY_OR_MOVE : NONE;
		}
		
		@Override
		protected @Nullable Transferable createTransferable(final JComponent c) {
			return c instanceof FileIcon ? new FileListTransferable(Arrays.asList(((FileIcon) c).file), new ImageIcon(((FileIcon) c).icon)) : null;
		}
		
		// this doesn't seem to work
		@Override
		public @Nullable Icon getVisualRepresentation(final Transferable t) {
			if (t instanceof FileListTransferable)
				return ((FileListTransferable) t).icon;
			return null;
		}
		
		// no need to override exportDone, the file watcher takes care of all file modifications
		
		@Override
		public void exportAsDrag(final JComponent comp, final InputEvent e, final int action) {
			if (comp instanceof FileIcon) {
				final Image icon = ((FileIcon) comp).icon;
				if (icon != null) {
					setDragImage(icon);
					setDragImageOffset(new Point(icon.getWidth(null), icon.getHeight(null)));
				}
			}
			super.exportAsDrag(comp, e, action);
		}
		
	};
	
	public void createDropTarget(final Component comp) {
		comp.setDropTarget(new DropTarget(comp, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetListener() {
			@Override
			public void drop(final DropTargetDropEvent dtde) {
				try {
					if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
						dtde.rejectDrop();
						return;
					}
					// linking isn't supported (yet)
					if (dtde.getDropAction() == DnDConstants.ACTION_LINK) {
						dtde.rejectDrop();
						return;
					}
					dtde.acceptDrop(dtde.getDropAction());
					@SuppressWarnings("unchecked")
					final List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					final boolean copy = dtde.getDropAction() == DnDConstants.ACTION_COPY;
					final List<File> failed = new ArrayList<>();
					IOException lastException = null;
					for (final File f : files) {
						try {
							if (copy)
								Files.copy(f.toPath(), folder.toPath().resolve(f.getName()));
							else
								Files.move(f.toPath(), folder.toPath().resolve(f.getName()));
						} catch (final IOException e) {
							failed.add(f);
							lastException = e;
						}
					}
					if (lastException != null) {
						final IOException le = lastException;
						SwingUtilities.invokeLater(() -> {
							final JOptionPane op = new JOptionPane("Could not " + (copy ? "copy" : "move") + " " + files.size() + " file" + (files.size() == 1 ? "" : "s") + ".\n"
									+ "The last error message was: " + le.getLocalizedMessage(), JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION);
							op.createDialog("Error").setVisible(true);
						});
					}
				} catch (UnsupportedFlavorException | IOException e) {
					e.printStackTrace();
				}
			}
			
			@Override
			public void dragOver(final DropTargetDragEvent dtde) {
				if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					dtde.rejectDrag();
					return;
				}
				// linking isn't supported (yet)
				if (dtde.getDropAction() == DnDConstants.ACTION_LINK) {
					dtde.rejectDrag();
					return;
				}
				try {
					// prevent dragging from a window into itself (still allows to drag from/to subfolders)
					dtde.acceptDrag(dtde.getDropAction()); // required to be able to get the list of files
					@SuppressWarnings("unchecked")
					final List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					for (final File f : files) {
						if (!f.getParentFile().equals(folder))
							return; // accept
					}
					dtde.rejectDrag();
					return;
				} catch (UnsupportedFlavorException | IOException e) {
					e.printStackTrace();
					dtde.rejectDrag();
					return;
				}
			}
			
			@Override
			public void dragExit(final DropTargetEvent dte) {}
			
			@Override
			public void dropActionChanged(final DropTargetDragEvent dtde) {
				dragOver(dtde);
			}
			
			@Override
			public void dragEnter(final DropTargetDragEvent dtde) {
				dragOver(dtde);
			}
		}, true));
	}
	
	public void reload() {
		
		final File[] contents = folder.listFiles();
		
		if (contents != null) {
			Arrays.sort(contents);
			
			// find out if anything changed at all, and exit early if nothing did
			final Component[] components = getComponents();
			if (contents.length == components.length || components.length == maxFiles && contents.length > maxFiles) {
				boolean changed = false;
				for (int i = 0; i < contents.length; i++) {
					final File f = contents[i];
					final Component c = i < components.length ? components[i] : null;
					if (!(c instanceof FileIcon)) {
						if (c instanceof JLabel && ((JLabel) c).getText().equals("...") && i == maxFiles - 1 && contents.length > maxFiles)
							return;
						changed = true;
						break;
					}
					final FileIcon fi = (FileIcon) c;
					if (!f.equals(fi.file) || !FileIcon.getFileToRun(f, useFolder).equals(fi.fileToRun)) {
						changed = true;
						break;
					}
				}
				if (!changed)
					return;
			}
			
			System.out.println("Updating file container for " + folder);
			
			removeAll();
			for (int i = 0; i < contents.length; i++) {
				final File f = contents[i];
				if (f.isHidden() || f.getName().startsWith("."))
					continue;
				add(new FileIcon(this, f, useFolder));
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
		} else {
			removeAll();
		}
		
		revalidate();
		repaint();
	}
	
	public int numFiles() {
		return getComponentCount();
	}
	
}
