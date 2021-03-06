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

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;

/**
 * A value that can have listeners added to it that will be notified of any changes to the value.
 * <p>
 * The value is automatically atomic and thread-safe.
 * <p>
 * Waiting on this monitor works, and any waiting threads will be notified when the value changes.
 * 
 * @param <T>
 */
public abstract class MonitoredValue<T> {
	
	public abstract T get();
	
	public abstract void set(T val);
	
	public abstract void setFromString(String val);
	
	@Override
	public abstract String toString();
	
	public static interface Listener<T> {
		void onChange(T value);
	}
	
	/**
	 * Adds a listener for this value, and also immediately calls the listener.
	 * 
	 * @param l
	 */
	public void addListener(final Listener<T> l) {
		listeners.add(l);
		l.onChange(get());
	}
	
	protected void valueChanged(final T value) {
		synchronized (this) {
			notifyAll();
		}
		for (final Listener<T> l : listeners)
			l.onChange(value);
	}
	
	private final Collection<Listener<T>> listeners = new ArrayList<>();
	
	public final static class MonitoredBoolean extends MonitoredValue<Boolean> {
		AtomicBoolean value;
		
		public MonitoredBoolean(final boolean val) {
			value = new AtomicBoolean(val);
		}
		
		public void set(final boolean val) {
			if (value.compareAndSet(!val, val))
				valueChanged(val);
		}
		
		@Override
		public void set(final Boolean val) {
			set((boolean) val);
		}
		
		@Override
		public Boolean get() {
			return value.get();
		}
		
		@Override
		public void setFromString(final String val) {
			set(Boolean.parseBoolean(val));
		}
		
		@Override
		public String toString() {
			return "" + value.get();
		}
		
		public Action createAction(final String name) {
			return new AbstractAction(name) {
				{
					putValue(Action.SELECTED_KEY, get());
				}
				
				@Override
				public void actionPerformed(final ActionEvent e) {
					set((boolean) getValue(Action.SELECTED_KEY));
				}
			};
		}
	}
	
	public final static class MonitoredInteger extends MonitoredValue<Integer> {
		AtomicInteger value;
		
		public MonitoredInteger(final int val) {
			value = new AtomicInteger(val);
		}
		
		public void set(final int val) {
			while (true) {
				final int oldVal = value.get();
				if (oldVal == val)
					return;
				if (value.compareAndSet(oldVal, val)) {
					valueChanged(val);
					return;
				}
			}
		}
		
		@Override
		public void set(final Integer val) {
			set((int) val);
		}
		
		@Override
		public Integer get() {
			return value.get();
		}
		
		@Override
		public void setFromString(final String val) {
			set(Integer.parseInt(val));
		}
		
		@Override
		public String toString() {
			return "" + value.get();
		}
		
		public BoundedRangeModel createModel(final int min, final int max) {
			return new DefaultBoundedRangeModel(value.get(), 0, min, max) {
				@Override
				public void setValue(final int n) {
					super.setValue(n);
					set(n);
				}
			};
		}
	}
	
	public final static class MonitoredString extends MonitoredValue<String> {
		AtomicReference<String> value;
		
		public MonitoredString(final String val) {
			value = new AtomicReference<>(val);
		}
		
		@Override
		public void set(final String val) {
			while (true) {
				final String oldVal = value.get();
				if (oldVal.equals(val))
					return;
				if (value.compareAndSet(oldVal, val)) {
					valueChanged(val);
					return;
				}
			}
		}
		
		@Override
		public String get() {
			return value.get();
		}
		
		@Override
		public void setFromString(final String val) {
			set(val);
		}
		
		@Override
		public String toString() {
			return "" + value.get();
		}
		
	}
	
}
