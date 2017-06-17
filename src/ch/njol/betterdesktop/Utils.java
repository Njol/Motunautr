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

import java.awt.Point;
import java.awt.Rectangle;

public abstract class Utils {
	
	public static Point insert(Rectangle in, Rectangle bounds) {
		in.x -= Math.max(0, in.getMaxX() - bounds.getMaxX());
		in.y -= Math.max(0, in.getMaxY() - bounds.getMaxY());
		in.x += Math.max(0, bounds.getMinX() - in.getMinX());
		in.y += Math.max(0, bounds.getMinY() - in.getMinY());
		return in.getLocation();
	}
	
}
