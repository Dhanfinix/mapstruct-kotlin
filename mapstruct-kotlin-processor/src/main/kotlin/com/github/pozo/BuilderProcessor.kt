package com.github.pozo

import com.github.pozo.BuilderGenerator.Builder.addBuilderMethod
import com.github.pozo.BuilderGenerator.Create.addCreateMethod
import com.github.pozo.BuilderGenerator.Field.addPrivateField
import com.github.pozo.BuilderGenerator.Setter.addSetterMethod
import com.google.auto.service.AutoService
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import kotlin.metadata.isData
import kotlin.metadata.jvm.KotlinClassMetadata
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter

@AutoService(Processor::class)
class BuilderProcessor : AbstractProcessor() {

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val (dataClasses, nonDataClasses) = roundEnv.getElementsAnnotatedWith(KotlinBuilder::class.java)
            .filterIsInstance<TypeElement>()
            .filter { isAnnotatedByKotlin(it) }
            .partition { isDataClass(it) }

        nonDataClasses.forEach {
            processingEnv.messager.printMessage(
                javax.tools.Diagnostic.Kind.ERROR,
                "@KotlinBuilder cannot be applied to non data classes",
                it
            )
        }

        dataClasses.forEach { generateBuilder(it) }

        return true
    }

    private fun isAnnotatedByKotlin(it: TypeElement): Boolean {
        processingEnv.messager.printMessage(
            javax.tools.Diagnostic.Kind.NOTE,
            "Checking if ${it.simpleName} is a Kotlin class..."
        )
        return it.annotationMirrors
            .any { annotation -> annotation.annotationType.toString() == Metadata::class.java.canonicalName }
    }

    private fun isDataClass(it: TypeElement): Boolean {
        val metadata = it.getAnnotation(Metadata::class.java)
        if (metadata == null) {
            processingEnv.messager.printMessage(
                javax.tools.Diagnostic.Kind.ERROR,
                "No Kotlin metadata found on ${it.simpleName}"
            )
            return false
        }

        val kotlinClassMetadata = KotlinClassMetadata.readStrict(metadata)
        return if (kotlinClassMetadata is KotlinClassMetadata.Class) {
            val isData = kotlinClassMetadata.kmClass.isData
            processingEnv.messager.printMessage(
                javax.tools.Diagnostic.Kind.NOTE,
                "${it.simpleName} isDataClass: $isData"
            )
            isData
        } else {
            processingEnv.messager.printMessage(
                javax.tools.Diagnostic.Kind.ERROR,
                "Unsupported Kotlin metadata type for ${it.simpleName}"
            )
            false
        }
    }

    private fun generateBuilder(typeElement: TypeElement) {
        val packageName = processingEnv.elementUtils.getPackageOf(typeElement).toString()
        val builderClassName = "${typeElement.simpleName}Builder"
        val classBuilder = TypeSpec.classBuilder(builderClassName)

        with(classBuilder) {
            this.addOriginatingElement(typeElement)
            this.addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            this.addTypeVariables(typeElement.typeParameters.map { TypeVariableName.get(it) })
            val constructors = ElementFilter.constructorsIn(typeElement.enclosedElements)

            constructors.stream()
                .flatMap { it.parameters.stream() }
                .peek { this.addSetterMethod(it, packageName, builderClassName) }
                .map { this.addPrivateField(it) }
                .toList()
                .apply {
                    this@with.addCreateMethod(typeElement, this)
                    this@with.addBuilderMethod(packageName, builderClassName)
                }
            this
        }.let {
            JavaFile.builder(packageName, it.build())
        }.apply {
            this.build().writeTo(processingEnv.filer)
        }
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(KotlinBuilder::class.java.canonicalName)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.RELEASE_17
    }

    override fun getSupportedOptions(): Set<String> {
        return setOf("kapt.kotlin.generated")
    }
}
