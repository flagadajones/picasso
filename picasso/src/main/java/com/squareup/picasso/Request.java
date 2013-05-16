package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.Future;

import static com.squareup.picasso.Utils.createKey;

class Request implements Runnable {
  static final int DEFAULT_RETRY_COUNT = 2;

  enum Type {
    CONTENT,
    FILE,
    STREAM,
    RESOURCE
  }

  enum LoadedFrom {
    MEMORY(Color.GREEN),
    DISK(Color.YELLOW),
    NETWORK(Color.RED);

    final int debugColor;

    private LoadedFrom(int debugColor) {
      this.debugColor = debugColor;
    }
  }

  final Picasso picasso;
  final String path;
  final int resourceId;
  final WeakReference<View> target;
  final PicassoBitmapOptions options;
  final List<Transformation> transformations;
  final Type type;
  final boolean skipCache;
  final boolean noFade;
  final int errorResId;
  final Drawable errorDrawable;
  final String key;

  Future<?> future;
  Bitmap result;
  LoadedFrom loadedFrom;
  int retryCount;
  boolean retryCancelled;

  Request(Picasso picasso, String path, int resourceId, View imageView,
      PicassoBitmapOptions options, List<Transformation> transformations, Type type,
      boolean skipCache, boolean noFade, int errorResId, Drawable errorDrawable) {
    this.picasso = picasso;
    this.path = path;
    this.resourceId = resourceId;
    this.target = new WeakReference<View>(imageView);
    this.options = options;
    this.transformations = transformations;
    this.type = type;
    this.skipCache = skipCache;
    this.noFade = noFade;
    this.errorResId = errorResId;
    this.errorDrawable = errorDrawable;
    this.retryCount = DEFAULT_RETRY_COUNT;
    this.key = createKey(this);
  }

  Object getTarget() {
    return target.get();
  }

  void complete() {
    if (result == null) {
      throw new AssertionError(
          String.format("Attempted to complete request with no result!\n%s", this));
    }

    View target = this.target.get();
    if (target != null) {
      Context context = picasso.context;
      boolean debugging = picasso.debugging;
        if(target instanceof ImageView)
        PicassoDrawable.setBitmap((ImageView)target, context, result, loadedFrom, noFade, debugging);
        else if (target instanceof TextView)
            PicassoDrawable.setBitmap((TextView)target, context, result, loadedFrom, noFade, debugging);

    }
  }

  void error() {
    View target = this.target.get();
    if (target == null) {
      return;
    }
    if (errorResId != 0) {
        if(target instanceof ImageView)
            ( (ImageView)target).setImageResource(errorResId);
        else if     (target instanceof TextView)
            ( (TextView)target).setCompoundDrawables(null,picasso.context.getResources().getDrawable(errorResId),null,null);
    } else if (errorDrawable != null) {
        if(target instanceof ImageView)
            ( (ImageView)target).setImageDrawable(errorDrawable);
        else if     (target instanceof TextView)
            ( (TextView)target).setCompoundDrawables(null,errorDrawable,null,null);
    }
  }

  @Override public void run() {
    try {
      // Change the thread name to contain the target URL for debugging purposes.
      Thread.currentThread().setName(Utils.THREAD_PREFIX + path);

      picasso.run(this);
    } catch (final Throwable e) {
      // If an unexpected exception happens, we should crash the app instead of letting the
      // executor swallow it.
      picasso.handler.post(new Runnable() {
        @Override public void run() {
          throw new RuntimeException("An unexpected exception occurred", e);
        }
      });
    } finally {
      Thread.currentThread().setName(Utils.THREAD_IDLE_NAME);
    }
  }

  @Override public String toString() {
    return "Request["
        + "hashCode="
        + hashCode()
        + ", picasso="
        + picasso
        + ", path="
        + path
        + ", resourceId="
        + resourceId
        + ", target="
        + target
        + ", options="
        + options
        + ", transformations="
        + transformationKeys()
        + ", future="
        + future
        + ", result="
        + result
        + ", retryCount="
        + retryCount
        + ", loadedFrom="
        + loadedFrom
        + ']';
  }

  String transformationKeys() {
    if (transformations == null) {
      return "[]";
    }

    StringBuilder sb = new StringBuilder(transformations.size() * 16);

    sb.append('[');
    boolean first = true;
    for (Transformation transformation : transformations) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append(transformation.key());
    }
    sb.append(']');

    return sb.toString();
  }
}
