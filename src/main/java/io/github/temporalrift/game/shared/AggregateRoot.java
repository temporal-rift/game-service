package io.github.temporalrift.game.shared;

import java.util.ArrayList;
import java.util.List;

public abstract class AggregateRoot {

    private final List<Object> domainEvents = new ArrayList<>();

    protected void registerEvent(Object event) {
        domainEvents.add(event);
    }

    public List<Object> pullEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}
