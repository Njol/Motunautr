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

import java.awt.Window;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

public interface Dwmapi extends Library {
	
	public final static Dwmapi INSTANCE = Native.loadLibrary("dwmapi", Dwmapi.class);
	
	int DwmEnableBlurBehindWindow(HWND hWnd, Dwmapi.DWM_BLURBEHIND[] pBlurBehind);
	
	public static class DWM_BLURBEHIND extends Structure {
		public long dwFlags; // bitflags: which of the following options are set
		public boolean fEnable;
		public @Nullable Pointer hRgnBlur = null;
		public boolean fTransitionOnMaximized;
		
		public DWM_BLURBEHIND(final boolean fEnable) {
			dwFlags = 1;
			this.fEnable = fEnable;
		}
		
		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList("dwFlags", "fEnable", "hRgnBlur", "fTransitionOnMaximized");
		}
	}
	
	public static void setBlurBehind(final Window w, final boolean blur) {
		final HWND hwnd = new HWND();
		hwnd.setPointer(Native.getComponentPointer(w));
//		final int error = 
		INSTANCE.DwmEnableBlurBehindWindow(hwnd, new Dwmapi.DWM_BLURBEHIND[] {new DWM_BLURBEHIND(blur)});
//		System.out.println(error);
	}
	
	int DwmSetWindowAttribute(HWND hwnd, int dwAttribute, PointerType pvAttribute, int cbAttribute);
	int DWMWA_EXCLUDED_FROM_PEEK = 12;

	public static void setExcludedFromPeek(final Window w, final boolean excluded) {
		final HWND hwnd = new HWND();
		hwnd.setPointer(Native.getComponentPointer(w));
//		final int error = 
		INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_EXCLUDED_FROM_PEEK, new IntByReference(excluded ? 1 : 0), 4);
//		System.out.println(error);
	}
	
}
