package ec.dmt.zkteco.domain;

import java.time.OffsetDateTime;

public record AuthenticationEvent(long eventId, String deviceSerial, String userId,
                                  OffsetDateTime authenticatedAt, int verifyType, int status) {
}
