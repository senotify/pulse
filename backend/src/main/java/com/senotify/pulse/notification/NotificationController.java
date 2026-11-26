package com.senotify.pulse.notification;

import com.senotify.pulse.common.CursorPage;
import com.senotify.pulse.common.PageParams;
import com.senotify.pulse.common.Viewer;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    /** {@code ids} limits which notifications are marked; omit the body (or the field) to mark everything. */
    record ReadRequest(List<Long> ids) {
    }

    private final NotificationRepository notifications;

    NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Transactional(readOnly = true)
    CursorPage<NotificationView> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer limit) {
        PageParams page = PageParams.of(cursor, limit);
        return CursorPage.of(notifications.page(Viewer.id(jwt), page.cursor(), page.fetchSize()), page,
                NotificationView::id);
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("count", notifications.unreadCount(Viewer.id(jwt)));
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void markRead(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) ReadRequest request) {
        long viewer = Viewer.id(jwt);
        if (request == null || request.ids() == null) {
            notifications.markRead(viewer, List.of());
        } else if (!request.ids().isEmpty()) {
            notifications.markRead(viewer, request.ids());
        }
    }
}
