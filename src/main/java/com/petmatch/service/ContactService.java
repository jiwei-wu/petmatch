package com.petmatch.service;

import com.petmatch.dto.ContactRequestResponse;
import com.petmatch.matching.MatchScorer;
import com.petmatch.repository.ContactRequestRepository;
import com.petmatch.repository.ContactRequestRepository.InfoRow;
import com.petmatch.repository.ContactRequestRepository.ParticipationRow;
import com.petmatch.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ContactService {

    static final String NOTIFY_REQUEST = "CONTACT_REQUEST";
    static final String NOTIFY_ACCEPTED = "CONTACT_ACCEPTED";
    static final String NOTIFY_DECLINED = "CONTACT_DECLINED";
    private static final int LIST_LIMIT = 50;

    private final ContactRequestRepository contactRepository;
    private final NotificationRepository notificationRepository;

    public ContactService(ContactRequestRepository contactRepository,
                          NotificationRepository notificationRepository) {
        this.contactRepository = contactRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public ContactRequestResponse create(Long matchId, Long userId, String message) {
        // 404 both when the match does not exist and when it is not yours, so ids cannot be probed
        ParticipationRow me = contactRepository.findParticipation(matchId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));

        if ("DISMISSED".equals(me.getMatchStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This match was dismissed");
        }
        if (contactRepository.countFromUser(matchId, me.getOtherUserId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The other person already sent you a request for this match; check your requests");
        }
        String cleaned = message.trim();
        if (contactRepository.insertIfAbsent(matchId, userId, me.getOtherUserId(), cleaned) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already sent a request for this match");
        }

        Long id = contactRepository.findIdByMatchAndSender(matchId, userId);
        // The recipient is told about THEIR OWN post (the other post from my point of view)
        notifyUser(me.getOtherUserId(), NOTIFY_REQUEST, matchId, me.getOtherPostId(), me.getScore());

        return new ContactRequestResponse(id, matchId, "SENT", "PENDING", cleaned,
                OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    @Transactional(readOnly = true)
    public List<ContactRequestResponse> listMine(Long userId) {
        return contactRepository.findForUser(userId, LIST_LIMIT).stream()
                .map(r -> new ContactRequestResponse(
                        r.getId(), r.getMatchId(), r.getDirection(), r.getStatus(), r.getMessage(),
                        MatchScorer.toTime(r.getCreatedEpoch()), r.getContactEmail()))
                .toList();
    }

    /** Only the RECIPIENT can accept or decline. Anyone else gets 404. */
    @Transactional
    public void decide(Long requestId, Long userId, boolean accept) {
        InfoRow info = contactRepository.findInfo(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (!info.getToUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        }

        String target = accept ? "ACCEPTED" : "DECLINED";
        if (target.equals(info.getStatus())) {
            return;                                   // repeating the same decision is harmless
        }
        if (!"PENDING".equals(info.getStatus())
                || contactRepository.updateStatusIfPending(requestId, target) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request was already decided");
        }

        // Tell the sender, about THEIR OWN post
        ParticipationRow sender = contactRepository.findParticipation(info.getMatchId(), info.getFromUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        notifyUser(info.getFromUserId(), accept ? NOTIFY_ACCEPTED : NOTIFY_DECLINED,
                info.getMatchId(), sender.getMyPostId(), sender.getScore());
    }

    private void notifyUser(Long userId, String type, Long matchId, Long myPostId, Double score) {
        String payload = "{\"matchId\":" + matchId
                + ",\"myPostId\":" + myPostId
                + ",\"score\":" + score + "}";
        notificationRepository.insert(userId, type, payload);
    }
}