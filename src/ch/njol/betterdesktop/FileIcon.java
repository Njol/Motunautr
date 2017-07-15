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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import sun.awt.shell.ShellFolder;

public class FileIcon extends JPanel {
	
	private final BDWindow window;
	
	private final File file;
	private final File fileToRun;
	private final boolean isExpandable;
	private @Nullable Image icon;
	private final JLabel /*iconLabel, */ nameLabel;
	
	private boolean hovered = false;
	
	public final static int SIZE_X = 70, SIZE_Y = 54;
	private final static int PADDING_X = 4, PADDING_Y = 3;
	private final static int ICON_SIZE = 32;
	private final static int LABEL_HEIGHT = SIZE_Y - 2 * PADDING_Y - ICON_SIZE;
	
	public FileIcon(final BDWindow window, final File file, final boolean useFolder) {
		this.window = window;
		this.file = file;
		fileToRun = useFolder ? getFileToRun(file) : file;
		
		updateIcon();
		
		setLayout(null);
		setMinimumSize(new Dimension(SIZE_X, SIZE_Y));
		setPreferredSize(getMinimumSize());
		setSize(getMinimumSize());
		setBackground(null);
		setOpaque(false);
		
		String name = file.getName();
		if (name.endsWith(".lnk") || name.endsWith(".url") || name.endsWith(".exe"))
			name = name.substring(0, name.lastIndexOf('.'));
		if (name.startsWith("!"))
			name = name.substring(1);
		add(nameLabel = new JLabel(name));
		nameLabel.setLocation(PADDING_X, PADDING_Y + ICON_SIZE);
		nameLabel.setSize(getWidth() - 2 * PADDING_X, LABEL_HEIGHT);
		nameLabel.setForeground(new Color(255, 255, 255));
		nameLabel.setBackground(null);
		nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
		if (nameLabel.getFontMetrics(nameLabel.getFont()).stringWidth(name) > nameLabel.getWidth())
			setToolTipText(name);
		
		isExpandable = file.isDirectory();
		
		final FixedMouseAdapter ma = new FixedMouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				if (e.getButton() == 1) {
					try {
						Desktop.getDesktop().open(fileToRun);
					} catch (final IOException e1) {
						e1.printStackTrace();
					}
				} else if (e.getButton() == 3) {
					toggleDropDown();
				} else if (e.getButton() == 2) {
					startRename();
				}
			}
			
			@Override
			public void mouseEntered(final MouseEvent e) {
				hovered = true;
				final Window window = SwingUtilities.getWindowAncestor(FileIcon.this);
				if (window instanceof BDWindow)
					window.repaint(); // bug workaround just like in BDWindow
				else
					repaint();
			}
			
			@Override
			public void mouseExited(final MouseEvent e) {
				hovered = false;
				repaint();
			}
		};
		ma.addToComponent(this);
	}
	
	@Override
	protected void paintComponent(@NonNull final Graphics g) {
		super.paintComponent(g);
		if (hovered) {
			g.setColor(new Color(0, 0, 0, 0.4f));
			g.fillRect(0, 0, getWidth(), getHeight());
		}
		final Graphics2D g2 = (Graphics2D) g;
		final Composite oldComp = g2.getComposite();
		if (!hovered)
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
		if (icon != null)
			g.drawImage(icon, (getWidth() - ICON_SIZE) / 2, PADDING_Y, ICON_SIZE, ICON_SIZE, null);
		if (isExpandable) {
//			g.drawImage(expandIcon, (getWidth() + ICON_SIZE) / 2 - EXPAND_ICON_SIZE / 2, PADDING_Y + ICON_SIZE - EXPAND_ICON_SIZE, EXPAND_ICON_SIZE, EXPAND_ICON_SIZE, null);
			final int expandX = (getWidth() + ICON_SIZE) / 2, expandY = PADDING_Y + ICON_SIZE;
			final int expandSize = 10;
			g.setColor(Color.white);
			g2.setStroke(new BasicStroke(3, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10));
			g.drawPolyline(
					new int[] {expandX - expandSize / 2, expandX, expandX + expandSize / 2},
					new int[] {expandY - expandSize / 2, expandY, expandY - expandSize / 2}, 3);
			g.setColor(Color.black);
			g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10));
			g.drawPolyline(
					new int[] {expandX - expandSize / 2, expandX, expandX + expandSize / 2},
					new int[] {expandY - expandSize / 2, expandY, expandY - expandSize / 2}, 3);
		}
		g2.setComposite(oldComp);
	}
	
	private @Nullable BDDropdown dropdown = null;
	
	@SuppressWarnings("null")
	public void toggleDropDown() {
		if (!isExpandable)
			return;
		if (dropdown != null && dropdown.isVisible()) {
			dropdown.dispose();
		} else if (dropdown != null) {
			dropdown.showFor(this, true);
		} else {
			dropdown = new BDDropdown(window, SwingUtilities.getWindowAncestor(this), file);
			dropdown.showFor(this, true);
		}
	}
	
	private void startRename() {
		String name = file.getName();
		String extension = "";
		if (!file.isDirectory() && (name.endsWith(".lnk") || name.endsWith(".url") || name.endsWith(".exe"))) {
			final int i = name.lastIndexOf('.');
			extension = name.substring(i);
			name = name.substring(0, i);
		}
		final String newName = (String) JOptionPane.showInputDialog(null, "", "Rename", JOptionPane.PLAIN_MESSAGE, null, null, name);
		if (newName != null) {
			final File newFile = new File(file.getParentFile(), newName + extension);
			if (file.renameTo(newFile))
				((BDFileContainer) getParent()).reload();
		}
	}
	
	private final static File getFileToRun(final File file) {
		if (file.isDirectory()) {
			// for a directory, return the first file in the directory. if there are no files (only dirs), repeat in the first directory
			final @NonNull File[] contents = file.listFiles();
			assert contents != null;
			Arrays.sort(contents);
			for (final File f : contents) {
				if (f.isHidden() || f.getName().startsWith(".") || f.isDirectory())
					continue;
				return getFileToRun(f);
			}
			if (contents.length > 0)
				return getFileToRun(contents[0]);
		}
		return file;
	}
	
	private final File getIconCacheFile() {
		return window.metaFolder.toPath().resolve("iconcache").resolve(window.folder.toPath().relativize(fileToRun.toPath().resolveSibling(fileToRun.getName() + ".png"))).toFile();
	}
	
	public final static @Nullable Path fileFromIconCacheFile(final BDWindow window, final Path iconCacheFile) {
		final String name = iconCacheFile.getFileName().toString();
		if (!name.endsWith(".png"))
			return null;
		return window.folder.toPath().resolve(window.metaFolder.toPath().resolve("iconcache").relativize(iconCacheFile.resolveSibling(name.substring(0, name.length() - 4))));
	}
	
	private final void updateIcon() {
		try {
			icon = ImageIO.read(getIconCacheFile());
		} catch (final IOException e) {}
		Main.threadPool.execute(() -> {
			if (!fileToRun.exists())
				return;
			try {
				icon = ShellFolder.getShellFolder(fileToRun).getIcon(true);
				repaint();
			} catch (final Exception e2) {
				e2.printStackTrace();
				final Icon ii = FileSystemView.getFileSystemView().getSystemIcon(fileToRun);
				if (ii != null && ii instanceof ImageIcon)
					icon = ((ImageIcon) ii).getImage();
				repaint();
			}
//				System.out.println(fi.icon.getClass());
//				System.out.println(Arrays.asList(fi.icon.getClass().getDeclaredMethods()));
			if (icon != null) {
				// Save icon to cache, but onnly if the file doesn't exist yet or has a different content.
				try {
					final File iconCacheFile = getIconCacheFile();
					iconCacheFile.getParentFile().mkdirs();
					final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
					assert icon != null;
					ImageIO.write(toRenderedImage(icon), "png", dataStream);
					final byte[] data = dataStream.toByteArray();
					byte[] existingData = null;
					try {
						existingData = Files.readAllBytes(iconCacheFile.toPath());
					} catch (final IOException e) {}
					if (!Arrays.equals(data, existingData)) {
						System.out.println("saving cached icon for " + file);
						Files.write(iconCacheFile.toPath(), data);
					}
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
		});
//		final SHFILEINFO[] info = {new SHFILEINFO()};
//		Shell32.INSTANCE.SHGetFileInfo(file.getAbsolutePath(), Shell32.FILE_ATTRIBUTE_NORMAL, info, info[0].size(), Shell32.SHGFI_ICONLOCATION | Shell32.SHGFI_USEFILEATTRIBUTES);
//		final int iconindex = info[0].iIcon;
//		final String iconFileName = new String(info[0].szDisplayName);
//		Shell32.INSTANCE.SHGetFileInfo(file.getAbsolutePath(), Shell32.FILE_ATTRIBUTE_NORMAL, info, info[0].size(), Shell32.SHGFI_ICON | Shell32.SHGFI_USEFILEATTRIBUTES);
//		System.out.println(file.getName() + " >> " + info[0].hIcon.getPointer() + " | " + iconFileName);
//		ICONINFO info2 = new ICONINFO();
//		boolean success = User32.INSTANCE.GetIconInfo(info[0].hIcon, info2);
//		if (success) {
//			info2.hbmColor
//		}
//
//		return ((ImageIcon) FileSystemView.getFileSystemView().getSystemIcon(file)).getImage();
	}
	
	public static RenderedImage toRenderedImage(final Image img) {
		if (img instanceof RenderedImage)
			return (RenderedImage) img;
		final int savedSize = 64;
		final BufferedImage b = new BufferedImage(savedSize, savedSize, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = b.createGraphics();
		g.drawImage(img, 0, 0, savedSize, savedSize, null);
		g.dispose();
		return b;
	}
	
//	@SuppressWarnings("null")
//	static Image expandIcon;
//	private final static int EXPAND_ICON_SIZE = 16;
//	static {
//		try {
//			expandIcon = ImageIO.read(new File("./images/expand.png")).getScaledInstance(EXPAND_ICON_SIZE, EXPAND_ICON_SIZE, Image.SCALE_SMOOTH);
//		} catch (final IOException e) {
//			e.printStackTrace();
//		}
//	}

}
