/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.timfreiheit.mozart.utils

import android.support.v4.media.session.MediaSessionCompat
import android.text.TextUtils
import de.timfreiheit.mozart.model.Playlist
import java.util.*

/**
 * Utility class to help on queue related tasks.
 */

internal fun Playlist.createMediaQueue(): List<MediaSessionCompat.QueueItem> {

    val queue = ArrayList<MediaSessionCompat.QueueItem>()
    val playlist1 = playlist
    for (i in playlist1.indices) {
        val track = playlist1[i]

        // We don't expect queues to change after created, so we use the item index as the
        // queueId. Any other number unique in the queue would work.
        val item = MediaSessionCompat.QueueItem(
                track.description, i.toLong())
        queue.add(item)

    }

    return queue
}

internal fun List<MediaSessionCompat.QueueItem>.getMusicIndex(mediaId: String): Int {
    return this.indexOfFirst { mediaId == it.description.mediaId }
}

internal fun List<MediaSessionCompat.QueueItem>.getMusicIndex(queueId: Long): Int {
    return this.indexOfFirst { queueId == it.queueId }
}

internal fun List<MediaSessionCompat.QueueItem>?.isIndexPlayable(index: Int): Boolean {
    return this != null && index >= 0 && index < this.size
}

/**
 * Determine if two queues contain identical media id's in order.
 *
 * @param list1 containing [MediaSessionCompat.QueueItem]'s
 * @param list2 containing [MediaSessionCompat.QueueItem]'s
 * @return boolean indicating whether the queue's match
 */
internal fun areQueuesEqual(list1: List<MediaSessionCompat.QueueItem>?,
           list2: List<MediaSessionCompat.QueueItem>?): Boolean {
    if (list1 === list2) {
        return true
    }
    if (list1 == null || list2 == null) {
        return false
    }
    if (list1.size != list2.size) {
        return false
    }
    for (i in list1.indices) {
        if (list1[i].queueId != list2[i].queueId) {
            return false
        }
        if (!TextUtils.equals(list1[i].description.mediaId,
                list2[i].description.mediaId)) {
            return false
        }
    }
    return true
}

