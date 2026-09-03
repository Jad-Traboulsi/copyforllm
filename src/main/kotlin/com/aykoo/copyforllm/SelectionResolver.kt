package com.aykoo.copyforllm

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Turns the Navigatables a project view selection hands to an action into the
 * VirtualFiles they stand for, shared by every CopyForLlm+ action.
 */
object SelectionResolver {

    private val logger = Logger.getInstance(SelectionResolver::class.java)

    fun resolve(navigatables: Array<out Navigatable?>): List<VirtualFile> =
        navigatables.mapNotNull { resolveVirtualFile(it) }.distinct()

    /**
     * Attempts to resolve a VirtualFile from various common Navigatable types found in IDE contexts.
     */
    private fun resolveVirtualFile(navigatable: Navigatable?): VirtualFile? {
        if (navigatable == null) return null

        var virtualFile: VirtualFile? = null

        // 1. Check if it IS a VirtualFile already
        if (navigatable is VirtualFile) {
            virtualFile = navigatable
        }

        // 2. Check if it's a PsiElement
        if (virtualFile == null && navigatable is PsiElement) {
            virtualFile = when (navigatable) {
                is PsiFile -> navigatable.virtualFile
                is PsiDirectory -> navigatable.virtualFile
                else -> navigatable.containingFile?.virtualFile // Fallback for other elements
            }
        }

        // 3. Check common Project View Node types (interface/base class)
        if (virtualFile == null && navigatable is ProjectViewNode<*>) {
            virtualFile = navigatable.virtualFile
        }

        // 4. Check AbstractTreeNode types (common base for tree nodes)
        if (virtualFile == null && navigatable is AbstractTreeNode<*>) {
            when (val value = navigatable.value) {
                is PsiElement -> virtualFile = when (value) {
                    is PsiFile -> value.virtualFile
                    is PsiDirectory -> value.virtualFile
                    else -> value.containingFile?.virtualFile
                }

                is VirtualFile -> virtualFile = value
            }
        }

        if (virtualFile == null) {
            logger.warn("Could not resolve VirtualFile for Navigatable type: ${navigatable.javaClass.name}")
        }

        return virtualFile
    }
}
