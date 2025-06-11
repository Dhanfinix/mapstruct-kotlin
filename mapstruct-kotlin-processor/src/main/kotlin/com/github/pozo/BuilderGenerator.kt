package com.github.pozo

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import java.util.Locale
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

object BuilderGenerator {

    object Field {
        fun TypeSpec.Builder.addPrivateField(variableElement: VariableElement): FieldSpec {
            return FieldSpec.builder(
                variableElement.type(),
                variableElement.name(),
                Modifier.PRIVATE
            ).build().apply {
                this@addPrivateField.addField(this)
            }
        }
    }

    object Setter {
        fun TypeSpec.Builder.addSetterMethod(
            variableElement: VariableElement, packageName: String, builderClassName: String
        ): VariableElement {
            createSetterMethodSpec(
                variableElement.name(),
                packageName,
                builderClassName,
                variableElement.type()
            ).apply {
                this@addSetterMethod.addMethod(this)
            }
            return variableElement
        }

        private fun createSetterMethodSpec(
            fieldName: String, packageName: String, builderClassName: String, fieldType: TypeName
        ): MethodSpec {
            return MethodSpec.methodBuilder("set${fieldName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }}")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(packageName, builderClassName))
                .addParameter(fieldType, fieldName)
                .addStatement("this.\$N = \$N", fieldName, fieldName)
                .addStatement("return this")
                .build()
        }
    }

    object Create {
        private const val METHOD_NAME = "create"

        fun TypeSpec.Builder.addCreateMethod(it: TypeElement, fields: List<FieldSpec>) {
            val guessedReturnValueType = ClassName.bestGuess(it.qualifiedName.toString())
            val createMethod = createCreateMethodSpec(guessedReturnValueType, fields)
            this.addMethod(createMethod)
        }

        private fun createCreateMethodSpec(
            guessedReturnValueType: ClassName, fields: List<FieldSpec>
        ): MethodSpec {
            return MethodSpec.methodBuilder(METHOD_NAME)
                .addModifiers(Modifier.PUBLIC)
                .returns(guessedReturnValueType)
                .addStatement(
                    "return new \$T(\$L)",
                    guessedReturnValueType,
                    fields.joinToString { it.name }
                )
                .build()
        }
    }

    object Builder {
        private const val METHOD_NAME = "builder"

        fun TypeSpec.Builder.addBuilderMethod(packageName: String, builderClassName: String) {
            val builderMethod = createBuilderMethodSpec(packageName, builderClassName)
            this.addMethod(builderMethod)
        }

        private fun createBuilderMethodSpec(packageName: String, builderClassName: String): MethodSpec {
            return MethodSpec.methodBuilder(METHOD_NAME)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(packageName, builderClassName))
                .addStatement("return new \$T()", ClassName.get(packageName, builderClassName))
                .build()
        }
    }

    fun VariableElement.name(): String = this.simpleName.toString()

    fun VariableElement.type(): TypeName = ClassName.get(this.asType())
}
