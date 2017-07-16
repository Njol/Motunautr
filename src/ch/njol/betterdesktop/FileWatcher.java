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
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.PriorityBlockingQueue;

import com.sun.nio.file.ExtendedWatchEventModifier;

public class FileWatcher {
	
	public interface FileListener {
		public void fileChanged(Path path, Kind<Path> kind);
	}
	
	/**
	 * Listens for file updates, but consolidates events inside a directory into single events.
	 */
	public abstract static class DirectoryListener implements FileListener, Comparable<DirectoryListener> {
		
		private final long delayMS;
		
		/**
		 * @param delayMS The maximum time interval in milliseconds between events to be consolidated into a single event
		 */
		public DirectoryListener(final long delayMS) {
			this.delayMS = delayMS;
		}
		
		private volatile long nextUpdate;
		
		@Override
		public final int compareTo(final DirectoryListener other) {
			if (other == this)
				return 0;
			final long tu = nextUpdate, ou = other.nextUpdate;
			return tu < ou ? -1 : tu > ou ? 1 : 0;
		}
		
		@Override
		public final void fileChanged(final Path path, final Kind<Path> kind) {
			if (ignoreChange(path))
				return;
			synchronized (listenersToUpdate) { // make this section atomic with regard to the one in the thread
				listenersToUpdate.remove(this);
				nextUpdate = System.currentTimeMillis() + delayMS;
				listenersToUpdate.add(this);
			}
			thread.interrupt();
		}
		
		public boolean ignoreChange(final Path path) {
			return false;
		}
		
		public abstract void directoryChanged();
		
		private static PriorityBlockingQueue<DirectoryListener> listenersToUpdate = new PriorityBlockingQueue<>();
		
		// a single thread should be enough - this program doesn't listen to hundreds of folders
		private static Thread thread = new Thread() {
			@Override
			public void run() {
				while (true) {
					final DirectoryListener next;
					
					try {
						next = listenersToUpdate.take();
					} catch (final InterruptedException e) {
						continue;
					}
					
					// if there is an event, wait until its desired update time or until notified again, in which case restart the whole loop
					// (maybe another listener got an earlier time now that the one we're waiting for if both got new events)
					final long nextUpdate = next.nextUpdate;
					final long toWait = nextUpdate - System.currentTimeMillis();
					if (toWait > 0) {
						synchronized (this) {
							try {
								Thread.sleep(toWait);
							} catch (final InterruptedException e) {}
						}
						if (nextUpdate > System.currentTimeMillis()) {
							// if we're interrupted early, re-add the listener to the queue and restart with taking the earliest listener (which may be this one or not)
							synchronized (listenersToUpdate) {
								// the listener can already be in the queue if an event happened while waiting
								if (!listenersToUpdate.contains(next))
									listenersToUpdate.add(next);
							}
							continue;
						}
					}
					
					next.directoryChanged();
				}
			}
		};
		static {
			thread.setDaemon(true);
			thread.start();
		}
	}
	
	private final static class ListenerAndPath {
		private final Path startPath;
		private final FileListener listener;
		
		public ListenerAndPath(final Path relativePath, final FileListener listener) {
			startPath = relativePath;
			this.listener = listener;
		}
	}
	
	private final ConcurrentLinkedDeque<ListenerAndPath> listeners = new ConcurrentLinkedDeque<>();
	
	public void addListener(final Path startPath, final FileListener listener) {
		listeners.add(new ListenerAndPath(toCanonicalPath(startPath), listener));
	}
	
	private final static Path toCanonicalPath(final Path path) {
		try {
			return path.toFile().getCanonicalFile().toPath();
		} catch (final IOException e) {
			return path.toAbsolutePath();
		}
	}
	
	private final WatchService watcher;
	
	private volatile boolean running = true;
	private final Thread thread = new Thread() {
		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			while (running) {
				try {
					final WatchKey watchKey = watcher.take();
					for (final WatchEvent<?> event : watchKey.pollEvents()) {
						Path changed = (Path) event.context();
						if (changed == null || event.kind() == StandardWatchEventKinds.OVERFLOW) {
							System.out.println("bad file watch event: " + event);
							continue;
						}
						changed = watchedDirectory.resolve(changed);
						for (final ListenerAndPath x : listeners) {
							if (Thread.interrupted() && !running)
								return;
							if (changed.startsWith(x.startPath)) {
								x.listener.fileChanged(changed, (Kind<Path>) event.kind());
							}
						}
					}
					watchKey.reset();
				} catch (final InterruptedException e) {}
			}
		}
	};
	
	public final Path watchedDirectory;
	
	public FileWatcher(final Path watchedDirectory) throws IOException {
		this.watchedDirectory = toCanonicalPath(watchedDirectory);
		watcher = FileSystems.getDefault().newWatchService();
		watchedDirectory.register(watcher, new Kind[] {StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY}, ExtendedWatchEventModifier.FILE_TREE);
		thread.setDaemon(true);
		thread.start();
	}
	
	public void close() {
		running = false;
		thread.interrupt();
		listeners.clear();
		try {
			watcher.close();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		while (thread.isAlive()) {
			try {
				thread.join();
			} catch (final InterruptedException e) {}
		}
	}
	
}
