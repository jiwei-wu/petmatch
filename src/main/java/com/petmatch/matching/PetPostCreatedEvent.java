package com.petmatch.matching;

/** Published after a post has been saved. Listeners react without the poster waiting for them. */
public record PetPostCreatedEvent(Long postId) {
}
