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
public class EmojiActor extends AbstractLoggingActor {
    private static Map<String, String> dictionary;
    static
    {
        dictionary = new HashMap<>();
        dictionary.put("hello", ":)");
        dictionary.put("tired", ":'(");
        dictionary.put("goodbye", ":‑[");
    }

    public EmojiActor() {
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
        return Props.create(EmojiActor.class, EmojiActor::new);
    }
}
