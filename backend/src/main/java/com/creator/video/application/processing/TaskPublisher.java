package com.creator.video.application.processing;

import com.creator.video.domain.DispatchClaim;
import com.creator.video.domain.PublicationResult;

public interface TaskPublisher {
    PublicationResult publish(DispatchClaim claim);
}
