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

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.BaseTSD.DWORD_PTR;

public interface Shell32 extends Library {
	public final static Shell32 INSTANCE = Native.loadLibrary("Shell32", Shell32.class);
	
	int MAX_PATH = 260;
	
	int FILE_ATTRIBUTE_NORMAL = 0x80;
	
	int SHGFI_USEFILEATTRIBUTES = 0x000000010;
	int SHGFI_ICON = 0x000000100;
	int SHGFI_LARGEICON = 0x000000000;
	int SHGFI_ICONLOCATION = 0x000001000;
	
	DWORD_PTR SHGetFileInfo(String pszPath, int dwFileAttributes, SHFILEINFO[] psfi, int cbFileInfo, int uFlags);
	
}
