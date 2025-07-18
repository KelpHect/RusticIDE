// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent


interface ChainedParentEntity : WorkspaceEntity {
  val child: List<ChainedEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChainedParentEntity> {
    override var entitySource: EntitySource
    var child: List<ChainedEntity.Builder>
  }

  companion object : EntityType<ChainedParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyChainedParentEntity(
  entity: ChainedParentEntity,
  modification: ChainedParentEntity.Builder.() -> Unit,
): ChainedParentEntity {
  return modifyEntity(ChainedParentEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChainedEntity : WorkspaceEntity {
  val data: String
  @Parent
  val parent: ChainedEntity?
  val child: ChainedEntity?
  @Parent
  val generalParent: ChainedParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChainedEntity> {
    override var entitySource: EntitySource
    var data: String
    var parent: ChainedEntity.Builder?
    var child: ChainedEntity.Builder?
    var generalParent: ChainedParentEntity.Builder?
  }

  companion object : EntityType<ChainedEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyChainedEntity(
  entity: ChainedEntity,
  modification: ChainedEntity.Builder.() -> Unit,
): ChainedEntity {
  return modifyEntity(ChainedEntity.Builder::class.java, entity, modification)
}
//endregion
