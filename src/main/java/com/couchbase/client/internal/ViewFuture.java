/**
 * Copyright (C) 2009-2011 Couchbase, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALING
 * IN THE SOFTWARE.
 */

package com.couchbase.client.internal;

import com.couchbase.client.protocol.views.ViewResponse;
import com.couchbase.client.protocol.views.ViewResponseWithDocs;
import com.couchbase.client.protocol.views.ViewRow;
import com.couchbase.client.protocol.views.ViewRowWithDocs;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.CheckedOperationTimeoutException;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationStatus;

/**
 * A ViewFuture.
 */
public class ViewFuture extends HttpFuture<ViewResponse> {
  private final AtomicReference<BulkFuture<Map<String, Object>>> multigetRef;

  public ViewFuture(CountDownLatch latch, long timeout) {
    super(latch, timeout);
    this.multigetRef =
        new AtomicReference<BulkFuture<Map<String, Object>>>(null);
  }

  @Override
  public ViewResponse get(long duration, TimeUnit units)
    throws InterruptedException, ExecutionException, TimeoutException {

    if (!latch.await(duration, units)) {
      if (op != null) {
        op.timeOut();
      }
      status = new OperationStatus(false, "Timed out");
      throw new TimeoutException("Timed out waiting for operation");
    }

    if (op != null && op.hasErrored()) {
      status = new OperationStatus(false, op.getException().getMessage());
      throw new ExecutionException(op.getException());
    }

    if (op != null && op.isCancelled()) {
      status = new OperationStatus(false, "Operation Cancelled");
      throw new ExecutionException(new RuntimeException("Cancelled"));
    }

    if (op != null && op.isTimedOut()) {
      status = new OperationStatus(false, "Timed out");
      throw new ExecutionException(new CheckedOperationTimeoutException(
          "Operation timed out.", (Operation)op));
    }

    if (multigetRef.get() == null) {
      return null;
    }

    Map<String, Object> docMap = multigetRef.get().get();
    final ViewResponseWithDocs view = (ViewResponseWithDocs) objRef.get();
    Collection<ViewRow> rows = new LinkedList<ViewRow>();
    Iterator<ViewRow> itr = view.iterator();

    while (itr.hasNext()) {
      ViewRow r = itr.next();
      rows.add(new ViewRowWithDocs(r.getId(), r.getKey(), r.getValue(),
          docMap.get(r.getId())));
    }
    return new ViewResponseWithDocs(rows, view.getErrors());
  }

  public void set(ViewResponse viewResponse,
      BulkFuture<Map<String, Object>> oper, OperationStatus s) {
    objRef.set(viewResponse);
    multigetRef.set(oper);
    status = s;
  }
}
