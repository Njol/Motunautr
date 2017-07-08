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

package ch.njol.betterdesktop.win32;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef.HICON;

@NonNullByDefault({})
public final class SHFILEINFO extends Structure {
	public HICON hIcon;
	public int iIcon;
	public int dwAttributes;
	public byte[] szDisplayName = new byte[Shell32.MAX_PATH];
	public byte[] szTypeName = new byte[80];
	
	@Override
	protected List<String> getFieldOrder() {
		return Arrays.asList("hIcon", "iIcon", "dwAttributes", "szDisplayName", "szTypeName");
	}
}
