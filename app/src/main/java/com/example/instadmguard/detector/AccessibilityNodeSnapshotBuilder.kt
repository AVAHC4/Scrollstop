package com.example.instadmguard.detector

import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityNodeSnapshotBuilder {

    private const val MAX_NODES = 250

    fun build(root: AccessibilityNodeInfo): NodeSnapshot {
        val texts = linkedSetOf<String>()
        val contentDescriptions = linkedSetOf<String>()
        val viewIds = linkedSetOf<String>()
        val classNames = linkedSetOf<String>()
        val counter = NodeCounter()

        walk(
            node = root,
            texts = texts,
            contentDescriptions = contentDescriptions,
            viewIds = viewIds,
            classNames = classNames,
            counter = counter,
        )

        return NodeSnapshot(
            packageName = root.packageName?.toString(),
            texts = texts,
            contentDescriptions = contentDescriptions,
            viewIds = viewIds,
            classNames = classNames,
            nodeCount = counter.count,
        )
    }

    private fun walk(
        node: AccessibilityNodeInfo?,
        texts: MutableSet<String>,
        contentDescriptions: MutableSet<String>,
        viewIds: MutableSet<String>,
        classNames: MutableSet<String>,
        counter: NodeCounter,
    ) {
        if (node == null || counter.count >= MAX_NODES) {
            return
        }

        counter.count += 1
        collect(node.text, texts)
        collect(node.contentDescription, contentDescriptions)
        collect(node.viewIdResourceName, viewIds)
        collect(node.className, classNames)

        for (index in 0 until node.childCount) {
            val child = node.getChild(index)
            if (child != null) {
                walk(
                    node = child,
                    texts = texts,
                    contentDescriptions = contentDescriptions,
                    viewIds = viewIds,
                    classNames = classNames,
                    counter = counter,
                )
                recycleNode(child)
            }
        }
    }

    private fun collect(value: CharSequence?, target: MutableSet<String>) {
        val text = value?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            target += text
        }
    }

    @Suppress("DEPRECATION")
    private fun recycleNode(node: AccessibilityNodeInfo) {
        node.recycle()
    }

    private class NodeCounter(var count: Int = 0)
}
