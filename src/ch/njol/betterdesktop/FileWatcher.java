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
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.jdt.annotation.Nullable;

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
		public int compareTo(final DirectoryListener o) {
			final long tu = nextUpdate, ou = o.nextUpdate;
			return tu < ou ? -1 : tu > ou ? 1 : 0;
		}
		
		@Override
		public void fileChanged(final Path path, final Kind<Path> kind) {
			if (ignoreChange(path))
				return;
			listenersToUpdate.remove(this);
			nextUpdate = System.currentTimeMillis() + delayMS;
			listenersToUpdate.add(this);
			synchronized (thread) {
				thread.notifyAll();
			}
		}
		
		public boolean ignoreChange(final Path path) {
			return false;
		}
		
		public abstract void directoryChanged();
		
		private static ConcurrentSkipListSet<DirectoryListener> listenersToUpdate = new ConcurrentSkipListSet<>();
		
		// a single thread should be enough - this program doesn't listen to hundreds of folders
		private static Thread thread = new Thread() {
			@Override
			public void run() {
				while (true) {
					final DirectoryListener next = listenersToUpdate.pollFirst();
					
					// wait indefinitely while there is nothing to do
					if (next == null) {
						try {
							synchronized (this) {
								this.wait();
							}
						} catch (final InterruptedException e) {}
						continue;
					}
					
					// if there is an event, wait until its desired update time or until notified again, in which case restart the whole loop
					// (maybe another listener got an earlier time now that the one we're waiting for if both got new events)
					final long nextUpdate = next.nextUpdate;
					final long now = System.currentTimeMillis();
					if (nextUpdate > now) {
						synchronized (this) {
							try {
								this.wait(nextUpdate - now);
							} catch (final InterruptedException e) {
								continue;
							}
						}
					}
					
					listenersToUpdate.remove(next);
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
					final Path dir = registeredPaths.get(watchKey);
					if (dir == null)
						continue;
					for (final WatchEvent<?> event : watchKey.pollEvents()) {
						Path changed = (Path) event.context();
						if (changed == null || event.kind() == StandardWatchEventKinds.OVERFLOW) {
							System.out.println("bad file watch event: " + event);
							continue;
						}
						changed = dir.resolve(changed);
						for (final ListenerAndPath x : listeners) {
							if (Thread.interrupted())
								return;
							if (changed.startsWith(x.startPath)) {
								x.listener.fileChanged(changed, (Kind<Path>) event.kind());
							}
						}
						if (changed.toFile().isDirectory())
							registerDirectory(changed);
					}
					if (!watchKey.reset())
						registeredPaths.remove(watchKey);
				} catch (final InterruptedException e) {}
			}
		}
	};
	
	public final Path watchedDirectory;
	
	public FileWatcher(final Path watchedDirectory) throws IOException {
		this.watchedDirectory = toCanonicalPath(watchedDirectory);
		watcher = FileSystems.getDefault().newWatchService();
		registerDirectory(watchedDirectory);
		thread.setDaemon(true);
		thread.start();
	}
	
	private final ConcurrentHashMap<WatchKey, Path> registeredPaths = new ConcurrentHashMap<>();
	
	private void registerDirectory(final Path directory) {
		try {
			Files.walkFileTree(directory, new FileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
					final WatchKey watchKey = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
					registeredPaths.put(watchKey, dir);
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult postVisitDirectory(final Path dir, @Nullable final IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (final IOException e) {
			e.printStackTrace();
		}
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
