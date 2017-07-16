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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.Icon;

import org.eclipse.jdt.annotation.NonNull;

public class FileListTransferable implements Transferable {
	
	public final List<@NonNull File> files;
	public final Icon icon;
	
	public FileListTransferable(final List<File> files, final Icon icon) {
		this.files = files;
		this.icon = icon;
	}
	
	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] {DataFlavor.javaFileListFlavor};
	}
	
	@Override
	public boolean isDataFlavorSupported(final DataFlavor flavor) {
		return flavor.equals(DataFlavor.javaFileListFlavor);
	}
	
	@Override
	public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException, IOException {
		if (!isDataFlavorSupported(flavor))
			throw new UnsupportedFlavorException(flavor);
		return files;
	}
	
}
