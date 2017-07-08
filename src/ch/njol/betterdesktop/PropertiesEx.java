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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Properties;

public class PropertiesEx {
	
	private final Properties props = new Properties();
	
	public void load(final Reader r) throws IOException {
		props.load(r);
		for (final Field f : getClass().getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()) || f.getName().contains("$"))
				continue;
			final String val = props.getProperty(f.getName());
			if (val == null)
				return;
			try {
				if (f.getType() == int.class)
					f.set(this, Integer.parseInt(val));
				else if (f.getType() == float.class)
					f.set(this, Float.parseFloat(val));
				else if (f.getType() == String.class)
					f.set(this, val);
				else if (MonitoredValue.class.isAssignableFrom(f.getType()))
					((MonitoredValue<?>) f.get(this)).setFromString(val);
				else
					throw new IllegalArgumentException("" + f);
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void save(final Writer w) throws IOException {
		for (final Field f : getClass().getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()) || f.getName().contains("$"))
				continue;
			try {
				props.setProperty(f.getName(), "" + f.get(this));
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
		props.store(w, "");
	}
	
}
