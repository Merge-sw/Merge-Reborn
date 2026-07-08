package com.merge.merge.session.web;

import com.merge.merge.session.model.EndReason;

class InvalidEndReasonException extends RuntimeException {
    final EndReason reason;

    InvalidEndReasonException(EndReason reason) {
        super("End reason not client-settable: " + reason +
                ". Allowed: NAVIGATED_AWAY, EXHAUSTED.");
        this.reason = reason;
    }
}
