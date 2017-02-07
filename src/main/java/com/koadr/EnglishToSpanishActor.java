package com.koadr;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by craigpottinger on 2/6/17.
 */
public class EnglishToSpanishActor extends AbstractLoggingActor {
    private static Map<String, String> dictionary;
    static
    {
        dictionary = new HashMap<String, String>();
        dictionary.put("hello", "hola");
        dictionary.put("tired", "cansado");
        dictionary.put("goodbye", "adiós");
    }

    public EnglishToSpanishActor() {
        receive(
                ReceiveBuilder.match(
                    String.class, s -> {
                        if (dictionary.containsKey(s)) {
                            sender().tell(dictionary.get(s), ActorRef.noSender());
                        } else {
                            log().warning("{} not found :'(", s);
                        }
                    }
                ).
                    matchAny(o -> log().warning("received unknown message")).build()
        );
    }

    static Props props() {
        return Props.create(EnglishToSpanishActor.class, EnglishToSpanishActor::new);
    }


}
