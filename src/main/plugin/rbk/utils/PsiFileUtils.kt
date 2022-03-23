package com.dengzii.plugin.rbk.utils

import com.dengzii.plugin.rbk.BindInfo
import com.dengzii.plugin.rbk.BindType
import com.dengzii.plugin.rbk.Constants
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import java.util.*

/**
 *
 * @author https://github.com/dengzii
 */
object PsiFileUtils {

    private const val LAYOUT_REF_PREFIX = "R.layout."
    private const val LAYOUT_FILE_SUFFIX = ".xml"
    private const val ANDROID_ID_ATTR_NAME = "android:id"
    private const val ANDROID_ID_PREFIX = "@+id/"

    fun getViewInfoFromPsiFile(psiFile: PsiFile, project: Project?): Map<String, List<BindInfo>> {
        val layoutRefExpr = findLayoutStatement(psiFile)
        val layoutPsi: MutableList<PsiFile> = ArrayList()
        for (layoutName in layoutRefExpr) {
            val fileName = layoutName.substring(LAYOUT_REF_PREFIX.length) + LAYOUT_FILE_SUFFIX
            val psiFiles = FilenameIndex.getFilesByName(project!!, fileName, GlobalSearchScope.allScope(project))
            layoutPsi.addAll(psiFiles)
        }
        val layoutViews: MutableMap<String, List<BindInfo>> = HashMap()
        for (p in layoutPsi) {
            if (p is XmlFile) {
                layoutViews[p.getName()] = getAndroidViewInfoFrom(p)
            }
        }
        return layoutViews
    }

    fun getAndroidViewInfoFrom(xmlFile: XmlFile): List<BindInfo> {
        val result: MutableList<BindInfo> = ArrayList()
        visitLayoutXmlFile(xmlFile, xmlFile, result)
        return result
    }

    fun findLayoutStatement(psiFile: PsiFile): List<String> {
        val list: MutableList<String> = ArrayList()
        visitStatement(psiFile, list)
        return list
    }

    private fun visitLayoutXmlFile(psiFile: PsiFile, psiElement: PsiElement, result: MutableList<BindInfo>) {
        psiElement.acceptChildren(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (element is XmlTag) {
                    val id = element.getAttributeValue(ANDROID_ID_ATTR_NAME)
                    val type = element.name
                    if (id != null) {
                        val viewInfo = BindInfo(
                                if (type.contains(".")) type.substring(type.lastIndexOf(".") + 1) else type,
                                id.replace(ANDROID_ID_PREFIX, ""),
                                bindAnnotation = null,
                                type = BindType.View
                        )
                        result.add(viewInfo)
                    }
                }
                visitLayoutXmlFile(psiFile, element, result)
            }
        })
    }

    private fun visitStatement(psiElement: PsiElement, result: MutableList<String>) {
        psiElement.acceptChildren(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (isReferenceExpression(element)) {
                    val expression = element.text
                    if (expression.startsWith(LAYOUT_REF_PREFIX)) {
                        result.add(expression)
                    }
                }
                visitStatement(element, result)
            }
        })
    }

    private fun isReferenceExpression(element: PsiElement): Boolean {
        return isKotlinExpression(element) || isJavaExpression(element)
    }

    private fun isKotlinExpression(element: PsiElement): Boolean {
        return element.toString().contains("DOT_QUALIFIED_EXPRESSION")
    }

    private fun isJavaExpression(element: PsiElement): Boolean {
        return element is PsiReferenceExpression
    }
}