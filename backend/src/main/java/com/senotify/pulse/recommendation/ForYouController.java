package com.senotify.pulse.recommendation;

import com.senotify.pulse.common.Viewer;
import com.senotify.pulse.recommendation.ForYouService.ForYouPage;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline")
class ForYouController {

    private final ForYouService forYou;

    ForYouController(ForYouService forYou) {
        this.forYou = forYou;
    }

    /**
     * Ranked recommendations. Each item carries its source and a human-readable reason;
     * {@code explain=true} adds the feature vector behind the score.
     */
    @GetMapping("/for-you")
    ForYouPage forYou(@AuthenticationPrincipal Jwt jwt,
                      @RequestParam(required = false) String cursor,
                      @RequestParam(required = false) Integer limit,
                      @RequestParam(defaultValue = "false") boolean explain) {
        return forYou.page(Viewer.id(jwt), cursor, limit, explain);
    }
}
