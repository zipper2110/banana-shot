package org.litvin.points

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

/**
 * How a comment comes on screen and goes away. It is a setting next to the [CommentStyle], and it works with every
 * style. `CommentAnimations` gives the times and `CommentAss` draws it.
 */
enum class CommentAnimation {
    /**
     * The comment shows at once and goes away at once.
     * Comments saved before the animations existed use it, and new comments use it until the user selects an animation.
     */
    @JsonEnumDefaultValue
    NONE,

    /** The comment fades in at its start and fades out at its end. */
    FADE,
}
