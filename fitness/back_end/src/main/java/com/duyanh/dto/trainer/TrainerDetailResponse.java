package com.duyanh.dto.trainer;

import java.util.List;

public record TrainerDetailResponse(TrainerResponse profile, List<ScheduleItem> schedule) {
}
