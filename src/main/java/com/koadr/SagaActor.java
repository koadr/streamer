package com.koadr;


import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import static com.koadr.SagaStreamActor.RequestMessage;

public class SagaActor extends AbstractLoggingActor {

    public SagaActor(ActorRef sagaStream) {
        receive(
                ReceiveBuilder.match(
                        String.class, s -> {
                            sagaStream.tell(new RequestMessage(self(),s), ActorRef.noSender());
                        }
                ).
                        matchAny(o -> log().warning("received unknown message")).build()
        );
    }


    static Props props(ActorRef sagaStream) {
        return Props.create(SagaActor.class, () -> new SagaActor(sagaStream));
    }
}
