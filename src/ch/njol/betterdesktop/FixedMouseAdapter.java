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

import java.awt.Component;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

public class FixedMouseAdapter {
	
	private final static double CLICK_SENSITIVITY_SQ = 5 * 5;
	private final static int MULTI_CLICK_SENSITIVITY_MS = 500;
	
	private final MouseAdapter listener = new MouseAdapter() {
		
		private final static int NUM_BUTTONS = 3;
		private final Point[] startPoints = new Point[NUM_BUTTONS];
		private final int[] clickCounts = new int[NUM_BUTTONS];
		private final long[] lastClickTime = new long[NUM_BUTTONS];
		private final Point[] lastClickPos = new Point[NUM_BUTTONS];
		
		@Override
		public final void mousePressed(final MouseEvent e) {
			final int b = e.getButton() - 1;
			if (b >= 0 && b < NUM_BUTTONS)
				startPoints[b] = e.getLocationOnScreen();
			FixedMouseAdapter.this.mousePressed(e);
		}
		
		@Override
		public final void mouseDragged(final MouseEvent e) {
			for (int b = 0; b < NUM_BUTTONS; b++) {
				if (startPoints[b] != null
						&& (e.getModifiersEx() & InputEvent.getMaskForButton(b + 1)) != 0
						&& startPoints[b].distanceSq(e.getLocationOnScreen()) > CLICK_SENSITIVITY_SQ) {
					startPoints[b] = null;
					FixedMouseAdapter.this.mouseClickCancelled(e);
				}
			}
			FixedMouseAdapter.this.mouseDragged(e);
		}
		
		@Override
		public final void mouseMoved(final MouseEvent e) {
			FixedMouseAdapter.this.mouseMoved(e);
		}
		
		@Override
		public final void mouseReleased(final MouseEvent e) {
			FixedMouseAdapter.this.mouseReleased(e);
			final int b = e.getButton() - 1;
			if (b >= 0 && b < NUM_BUTTONS
					&& startPoints[b] != null
					&& startPoints[b].distanceSq(e.getLocationOnScreen()) <= CLICK_SENSITIVITY_SQ) {
				
				if (e.getWhen() > lastClickTime[b] + MULTI_CLICK_SENSITIVITY_MS
						|| lastClickPos[b] == null || lastClickPos[b].distanceSq(e.getLocationOnScreen()) > CLICK_SENSITIVITY_SQ)
					clickCounts[b] = 1;
				else
					clickCounts[b]++;
				
				lastClickPos[b] = e.getLocationOnScreen();
				lastClickTime[b] = e.getWhen();
				
				final MouseEvent newEvent = new MouseEvent(e.getComponent(), MouseEvent.MOUSE_CLICKED, e.getWhen(), e.getModifiers(), e.getY(), e.getY(), e.getXOnScreen(), e.getYOnScreen(), clickCounts[b], false, b + 1);
				FixedMouseAdapter.this.mouseClicked(newEvent);
				
				startPoints[b] = null;
			}
		}
		
		@Override
		public final void mouseClicked(final MouseEvent e) {
			// totally ignored
		}
		
		@Override
		public final void mouseEntered(final MouseEvent e) {
			FixedMouseAdapter.this.mouseEntered(e);
		}
		
		@Override
		public final void mouseExited(final MouseEvent e) {
			FixedMouseAdapter.this.mouseExited(e);
		}
	};
	
	public void addToComponent(final Component c) {
		c.addMouseListener(listener);
		c.addMouseMotionListener(listener);
	}
	
	protected void mousePressed(final MouseEvent e) {}
	
	protected void mouseDragged(final MouseEvent e) {}
	
	protected void mouseMoved(final MouseEvent e) {}
	
	protected void mouseClicked(final MouseEvent e) {}
	
	/**
	 * Called when a mouse click did not happen because the cursor moved too much
	 * 
	 * @param e A mouse drag event
	 */
	protected void mouseClickCancelled(final MouseEvent e) {}
	
	protected void mouseReleased(final MouseEvent e) {}
	
	protected void mouseEntered(final MouseEvent e) {}
	
	protected void mouseExited(final MouseEvent e) {}

}
