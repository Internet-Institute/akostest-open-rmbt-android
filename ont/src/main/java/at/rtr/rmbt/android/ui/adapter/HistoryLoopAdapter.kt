/*
 *
 *  Licensed under the Apache License, Version 2.0 (the “License”);
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an “AS IS” BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */
package at.rtr.rmbt.android.ui.adapter

import android.view.View
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.ItemHistoryBinding
import at.rtr.rmbt.android.databinding.ItemHistoryLoopBinding
import at.rtr.rmbt.android.util.bindWith
import at.specure.data.entity.History
import at.specure.data.entity.HistoryContainer

private const val ITEM_LOOP = 0
private const val ITEM_HISTORY = 1

class HistoryLoopAdapter : PagedListAdapter<HistoryContainer, HistoryLoopAdapter.Holder>(DIFF_CALLBACK) {
    var actionCallback: ((History) -> Unit)? = null

    override fun getItemViewType(position: Int): Int {
        val size = getItem(position)?.items?.size ?: 1
        return if (size == 1) {
            ITEM_HISTORY
        } else {
            ITEM_LOOP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = if (viewType == ITEM_LOOP) {
        LoopHolder(parent.bindWith(R.layout.item_history_loop))
    } else {
        HistoryHolder(parent.bindWith(R.layout.item_history))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        getItem(position)?.let { item ->
            holder.bind(position, item, actionCallback)
        }
    }

    class LoopHolder(val binding: ItemHistoryLoopBinding) : Holder(binding.root) {

        override fun bind(position: Int, item: HistoryContainer, actionCallback: ((History) -> Unit)?) {
            if (item.items.isEmpty()) {
                return
            }
            // todo median
            binding.item = item.items.last()
            binding.root.setOnClickListener {
                actionCallback?.invoke(item.items.first())
            }
        }
    }

    class HistoryHolder(val binding: ItemHistoryBinding) : Holder(binding.root) {

        override fun bind(position: Int, item: HistoryContainer, actionCallback: ((History) -> Unit)?) {
            binding.item = item.items.first()
            binding.root.setOnClickListener {
                actionCallback?.invoke(item.items.first())
            }
        }
    }

    abstract class Holder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(position: Int, item: HistoryContainer, actionCallback: ((History) -> Unit)?)
    }

    companion object {

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HistoryContainer>() {

            override fun areItemsTheSame(oldItem: HistoryContainer, newItem: HistoryContainer) =
                oldItem.reference.uuid == newItem.reference.uuid

            override fun areContentsTheSame(oldItem: HistoryContainer, newItem: HistoryContainer) =
                oldItem.reference.uuid == newItem.reference.uuid &&
                        oldItem.reference.time == newItem.reference.time &&
                        oldItem.items.size == newItem.items.size
        }
    }
}