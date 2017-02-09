package com.koadr;


import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.*;
import akka.stream.javadsl.*;
import akka.util.Timeout;

import java.util.Optional;
import static com.koadr.SagaActor.AddTranslation;
import static com.koadr.SagaActor.AddEmoji;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static akka.pattern.PatternsCS.ask;

public class SagaStreamActor extends AbstractLoggingActor  {
    private ActorRef sourceActorRef;

    public static class Start {
        private static Start ourInstance = new Start();

        public static Start getInstance() {
            return ourInstance;
        }

        private Start() {
        }
    }

    public static class Translation {
        final String underlying;
        final String type;

        public Translation(String underlying, String type) {
            this.underlying = underlying;
            this.type = type;
        }

        public String getUnderlying() {
            return underlying;
        }

        public String getType() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Translation)) return false;

            Translation that = (Translation) o;

            if (!underlying.equals(that.underlying)) return false;
            return type.equals(that.type);
        }

        @Override
        public int hashCode() {
            int result = underlying.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Translation{" +
                    "underlying='" + underlying + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    public static class Word implements Message {
        final ActorRef correlationId;
        final String underlying;
        final Optional<Translation> translation;
        final Optional<String> emoji;

        public Word(ActorRef correlationId, String underlying, Optional<Translation> translation, Optional<String> emoji) {
            this.correlationId = correlationId;
            this.underlying = underlying;
            this.translation = translation;
            this.emoji = emoji;
        }

        public ActorRef getCorrelationId() {
            return correlationId;
        }

        public String getUnderlying() {
            return underlying;
        }

        public Optional<String> getEmoji() {
            return emoji;
        }

        public Optional<Translation> getTranslation() {
            return translation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Word)) return false;

            Word word = (Word) o;

            if (!correlationId.equals(word.correlationId)) return false;
            if (!underlying.equals(word.underlying)) return false;
            if (!translation.equals(word.translation)) return false;
            return emoji.equals(word.emoji);
        }

        @Override
        public int hashCode() {
            int result = correlationId.hashCode();
            result = 31 * result + underlying.hashCode();
            result = 31 * result + translation.hashCode();
            result = 31 * result + emoji.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Word{" +
                    "correlationId=" + correlationId +
                    ", underlying='" + underlying + '\'' +
                    ", translation=" + translation +
                    ", emoji=" + emoji +
                    '}';
        }
    }

    public SagaStreamActor(ActorRef emoji, ActorRef translator, ActorMaterializer materializer) {
        receive(
                ReceiveBuilder.match(
                        Start.class, s -> {
                            final Source<Word, ActorRef> source = Source.actorRef(Integer.MAX_VALUE, OverflowStrategy.fail());
                            this.sourceActorRef = createGraph(emoji,translator,source).run(materializer);
                        }
                ).match(
                        Word.class, msg -> sourceActorRef.tell(msg, msg.getCorrelationId())
                ).
                    matchAny(o -> log().warning("received unknown message")).build()
        );

    }


    private static RunnableGraph<ActorRef> createGraph(ActorRef emoji, ActorRef translator, Source<Word, ActorRef> in) {
        final Sink<Pair<Word, Word>, CompletionStage<Done>> sink =
                Sink.foreach( pair -> System.out.printf("%s %s\n", pair.first(), pair.second()));

        return RunnableGraph.fromGraph(GraphDSL.create(in, sink, Keep.left(), (builder, src, sk) -> {
            final UniformFanOutShape<Word, Word> bcast = builder.add(Broadcast.create(2));
            final FanInShape2<Word, Word, Pair<Word, Word>> zip = builder.add(Zip.create());
            Timeout askTimeout = Timeout.apply(5, TimeUnit.SECONDS);

            Flow<Word, Word, NotUsed> translatorFlow =
                    Flow.of(Word.class)
                            .mapAsync(
                                    5,
                                    w -> {
                                        if (w.getTranslation().isPresent()) {
                                            return CompletableFuture.completedFuture(w);
                                        } else {
                                            return
                                                    ask(
                                                        translator,
                                                        w.getUnderlying(),
                                                        askTimeout)
                                                        .thenApply(t -> new Word(w.getCorrelationId(), w.getUnderlying(),Optional.of(new Translation((String) t,"ES")),w.getEmoji()));
                                        }
                                    }
                            ).map(w -> {
                                w.correlationId.tell(new AddTranslation(w.getUnderlying(),w.getTranslation().get()), ActorRef.noSender());
                                return w;

                    });

            Flow<Word, Word, NotUsed> emojiFlow =
                    Flow.of(Word.class)
                            .mapAsync(
                                    5,
                                    w -> {
                                        if (w.getEmoji().isPresent()) {
                                            return CompletableFuture.completedFuture(w);
                                        } else {
                                            return
                                                    ask(
                                                            emoji,
                                                            w.getUnderlying(),
                                                            askTimeout)
                                                            .thenApply(e -> new Word(w.getCorrelationId(), w.getUnderlying(),w.getTranslation(),Optional.of((String) e)));
                                        }
                                    }
                            ).map(w -> {
                        w.correlationId.tell(new AddEmoji(w.getUnderlying(),w.getEmoji().get()), ActorRef.noSender());
                        return w;

                    });

            builder.from(src).viaFanOut(bcast).via(builder.add(translatorFlow)).toInlet(zip.in0());
            builder.from(bcast).via(builder.add(emojiFlow)).toInlet(zip.in1());
            builder.from(zip.out()).to(sk);
            return ClosedShape.getInstance();
        }));

    }



    static Props props(ActorRef emoji, ActorRef translator, ActorMaterializer materializer) {
        return Props.create(SagaStreamActor.class, () -> new SagaStreamActor(emoji, translator, materializer));
    }
}
