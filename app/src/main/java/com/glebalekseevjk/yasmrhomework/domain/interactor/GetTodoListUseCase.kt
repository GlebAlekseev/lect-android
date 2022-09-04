package com.glebalekseevjk.yasmrhomework.domain.interactor

import com.glebalekseevjk.yasmrhomework.domain.entity.TodoItem
import com.glebalekseevjk.yasmrhomework.domain.repository.TodoItemsRepository
import kotlinx.coroutines.flow.Flow

class GetTodoListUseCase(private val todoItemsRepository: TodoItemsRepository) {
    operator fun invoke(): Flow<List<TodoItem>> = todoItemsRepository.getTodoList()
}