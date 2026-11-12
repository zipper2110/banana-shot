package org.litvin.points

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import org.litvin.shared.util.Edition

/**
 * How a comment comes on screen and goes away. It is a setting next to the [CommentStyle], and it works with every
 * style. `CommentAnimations` gives the times and `CommentAss` draws it.
 * The animations are for the paid version (docs/backlog-paid.md). Only [NONE] is free.
 */
enum class CommentAnimation(
    /** True for an animation of the paid version. The free version does not show it and does not draw it. */
    val paid: Boolean = true,
) {
    /**
     * The comment shows at once and goes away at once.
     * Comments saved before the animations existed use it, and new comments use it until the user selects an animation.
     */
    @JsonEnumDefaultValue
    NONE(paid = false),

    /** The comment fades in at its start and fades out at its end. */
    FADE,

    /** The comment grows from half size into place around its center while it fades in. It fades out at its end. */
    POP,

    /** The comment moves up into place while it fades in. It fades out at its end. */
    SLIDE_UP,
    ;

    /** True when this edition of the app has the animation. */
    val available: Boolean get() = !paid || Edition.isPaid

    /**
     * This animation, or [NONE] for a paid animation in the free version. The free version draws and edits the comment
     * without animation. The saved project keeps the animation until the user saves the comment again.
     */
    fun orAvailable(): CommentAnimation = if (available) this else NONE
}
