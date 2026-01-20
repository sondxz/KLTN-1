package com.web.dto.response;

import com.web.entity.Plant;
import com.web.entity.PlantMedia;

public interface PlantWithMedia {
    Plant getPlant();
    PlantMedia getMedia();
}
