/*
 * Copyright (c) 2016 Uber Technologies, Inc. (hoodie-dev-group@uber.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.hoodie.common.io.storage;

import com.uber.hoodie.common.util.ConsistencyGuard;
import com.uber.hoodie.exception.HoodieException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;

/**
 * Wrapper over <code>FSDataOutputStream</code> to keep track of the size of the written bytes. This
 * gives a cheap way to check on the underlying file size.
 */
public class SizeAwareFSDataOutputStream extends FSDataOutputStream {

  // A callback to call when the output stream is closed.
  private final Runnable closeCallback;
  // Keep track of the bytes written
  private final AtomicLong bytesWritten = new AtomicLong(0L);
  // Path
  private final Path path;
  // Consistency guard
  private final ConsistencyGuard consistencyGuard;

  public SizeAwareFSDataOutputStream(Path path, FSDataOutputStream out,
      ConsistencyGuard consistencyGuard, Runnable closeCallback) throws IOException {
    super(out);
    this.path = path;
    this.closeCallback = closeCallback;
    this.consistencyGuard = consistencyGuard;
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) throws IOException {
    bytesWritten.addAndGet(len);
    super.write(b, off, len);
  }

  @Override
  public void write(byte[] b) throws IOException {
    bytesWritten.addAndGet(b.length);
    super.write(b);
  }

  @Override
  public void close() throws IOException {
    super.close();
    try {
      consistencyGuard.waitTillFileAppears(path);
    } catch (TimeoutException e) {
      throw new HoodieException(e);
    }
    closeCallback.run();
  }

  public long getBytesWritten() {
    return bytesWritten.get();
  }

}
