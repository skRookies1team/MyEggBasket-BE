package com.rookies4.finalProject.websocket;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RealtimeSubscriptionManager {

    // sessionId
    private final Map<String, Map<String, String>> sessionSubs = new ConcurrentHashMap<>();

    // destination -> sessionId set
    private final Map<String, Set<String>> destinationSessions = new ConcurrentHashMap<>();

    public record UnsubResult(String destination, int remain) {}

    // 구독자 추가
    public int addSubscribe(String sessionId, String subscriptionId, String destination) {
        sessionSubs
                .computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(subscriptionId, destination);

        destinationSessions
                .computeIfAbsent(destination, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        return destinationSessions.get(destination).size(); // 해당 목적지에서의 구독자 수
    }

    public UnsubResult removeUnsubscribe(String sessionId, String subscriptionId) {
        Map<String, String> subs = sessionSubs.get(sessionId);
        if (subs == null) return new UnsubResult(null, 0);

        String destination = subs.remove(subscriptionId);
        if (destination == null) return new UnsubResult(null, 0);

        Set<String> sessions = destinationSessions.get(destination);
        if (sessions == null) return new UnsubResult(destination, 0);

        sessions.remove(sessionId);
        int remain = sessions.size();

        if (remain == 0) destinationSessions.remove(destination);
        if (subs.isEmpty()) sessionSubs.remove(sessionId);

        return new UnsubResult(destination, remain);
    }

    // disconnect 시 해당 세션이 빠진 뒤 "구독자 0이 된 destination들"만 반환
    public Set<String> removeAllByDisconnect(String sessionId) {
        Map<String, String> removed = sessionSubs.remove(sessionId);
        if (removed == null) return Set.of();

        Set<String> becameZero = new HashSet<>();

        for (String destination : removed.values()) {
            Set<String> sessions = destinationSessions.get(destination);
            if (sessions == null) continue;

            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                destinationSessions.remove(destination);
                becameZero.add(destination);
            }
        }
        return becameZero;
    }

    public int getSessionSubscriptionCount(String sessionId) {
        Map<String, String> subs = sessionSubs.get(sessionId);
        if (subs == null) return 0;
        return subs.size();
    }
}