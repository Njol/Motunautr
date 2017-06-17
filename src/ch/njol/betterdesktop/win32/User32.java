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

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.xml.internal.bind.v2.runtime.property.StructureLoaderBuilder;

public interface User32 extends Library {
	public final static User32 INSTANCE = Native.loadLibrary("User32", User32.class);
	
	int SetWindowCompositionAttribute(HWND hwnd, WindowCompositionAttributeData data);
	
	int ACCENT_ENABLE_BLURBEHIND = 3;
	
	class AccentPolicy extends Structure implements Structure.ByReference {
		public int AccentState;
		public int AccentFlags;
		public int GradientColor;
		public int AnimationId;
		
		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList("AccentState", "AccentFlags", "GradientColor", "AnimationId");
		}
	}
	
	int WCA_ACCENT_POLICY = 19;
	
	class WindowCompositionAttributeData extends Structure {
		public int Attribute;
		@SuppressWarnings("null")
		public AccentPolicy Data;
		public int SizeOfData;
		
		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList("Attribute", "Data", "SizeOfData");
		}
	}
	
	static void enableBlur(final Window w) {
		
		final AccentPolicy accent = new AccentPolicy();
		accent.AccentState = ACCENT_ENABLE_BLURBEHIND;
		
		final WindowCompositionAttributeData data = new WindowCompositionAttributeData();
		data.Attribute = WCA_ACCENT_POLICY;
		data.Data = accent;
		data.SizeOfData = accent.size();
		
		INSTANCE.SetWindowCompositionAttribute(new HWND(Native.getComponentPointer(w)), data);
		
	}
	
}
