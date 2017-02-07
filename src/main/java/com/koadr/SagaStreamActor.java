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

    public static class RequestMessage implements Message {
        final ActorRef correlationId;
        final String underlying;

        public RequestMessage(ActorRef correlationId, String underlying) {
            this.correlationId = correlationId;
            this.underlying = underlying;
        }

        public ActorRef getCorrelationId() {
            return correlationId;
        }

        public String getUnderlying() {
            return underlying;
        }
    }

    public SagaStreamActor(ActorRef emoji, ActorRef translator, ActorMaterializer materializer) {
        receive(
                ReceiveBuilder.match(
                        Start.class, s -> {
                            final Source<RequestMessage, ActorRef> source = Source.actorRef(Integer.MAX_VALUE, OverflowStrategy.fail());
                            this.sourceActorRef = createGraph(emoji,translator,source).run(materializer);
                        }
                ).match(
                        RequestMessage.class, msg -> sourceActorRef.tell(msg, msg.getCorrelationId())
                ).
                    matchAny(o -> log().warning("received unknown message")).build()
        );

    }

    private static RunnableGraph<ActorRef> createGraph(ActorRef emoji, ActorRef translator, Source<RequestMessage, ActorRef> in) {
        final Sink<Pair<String, String>, CompletionStage<Done>> sink =
                Sink.foreach( pair -> System.out.printf("%s %s\n", pair.first(), pair.second()));

        return RunnableGraph.fromGraph(GraphDSL.create(in, sink, Keep.left(), (builder, src, sk) -> {
            final UniformFanOutShape<RequestMessage, RequestMessage> bcast = builder.add(Broadcast.create(2));
            final FanInShape2<String, String, Pair<String, String>> zip = builder.add(Zip.create());
            Timeout askTimeout = Timeout.apply(5, TimeUnit.SECONDS);

            Flow<RequestMessage, String, NotUsed> translatorFlow =
                    Flow.of(RequestMessage.class)
                            .mapAsync(5, s -> ask(translator, s.getUnderlying(),askTimeout) )
                            .map(elem -> (String) elem);

            Flow<RequestMessage, String, NotUsed> emojiFlow =
                    Flow.of(RequestMessage.class)
                            .mapAsync(5, s -> ask(emoji, s.getUnderlying(),askTimeout) )
                            .map(elem -> (String) elem);

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
