package io.openaev.rest.stream.ai;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class AiSubscriber implements HttpResponse.BodySubscriber<Void> {
  protected static final Pattern dataLinePattern = Pattern.compile("^data: ?(.*)$");

  protected static String extractMessageData(String[] messageLines) {
    var s = new StringBuilder();
    for (var line : messageLines) {
      var m = dataLinePattern.matcher(line);
      if (m.matches()) {
        s.append(m.group(1));
      }
    }
    return s.toString();
  }

  protected final Consumer<? super String> messageDataConsumer;
  protected final CompletableFuture<Void> future;
  protected volatile Flow.Subscription subscription;
  protected volatile String deferredText;

  public AiSubscriber(Consumer<? super String> messageDataConsumer) {
    this.messageDataConsumer = messageDataConsumer;
    this.future = new CompletableFuture<>();
    this.subscription = null;
    this.deferredText = null;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    try {
      this.deferredText = "";
      this.subscription.request(1);
    } catch (Exception e) {
      this.future.completeExceptionally(e);
      this.subscription.cancel();
    }
  }

  @Override
  public void onNext(List<ByteBuffer> buffers) {
    try {
      // Volatile read
      var deferredText = this.deferredText;

      for (var buffer : buffers) {
        // TODO: Safe to assume multi-byte chars don't get split across buffers?
        var s = deferredText + UTF_8.decode(buffer);

        // -1 means don't discard trailing empty tokens ... so the final token will
        // be whatever is left after the last \n\n (possibly the empty string, but
        // not necessarily), which is the part we need to defer until the next loop
        // iteration
        var tokens = s.split("\n\n", -1);

        // Final token gets deferred, not processed here
        for (var i = 0; i < tokens.length - 1; i++) {
          var message = tokens[i];
          var lines = message.split("\n");
          var data = extractMessageData(lines);
          this.messageDataConsumer.accept(data);
        }

        // Defer the final token
        deferredText = tokens[tokens.length - 1];
      }

      // Volatile write
      this.deferredText = deferredText;

      this.subscription.request(1);
    } catch (Exception e) {
      this.future.completeExceptionally(e);
      this.subscription.cancel();
    }
  }

  @Override
  public void onError(Throwable e) {
    this.future.completeExceptionally(e);
  }

  @Override
  public void onComplete() {
    try {
      this.future.complete(null);
    } catch (Exception e) {
      this.future.completeExceptionally(e);
    }
  }

  @Override
  public CompletionStage<Void> getBody() {
    return this.future;
  }
}
