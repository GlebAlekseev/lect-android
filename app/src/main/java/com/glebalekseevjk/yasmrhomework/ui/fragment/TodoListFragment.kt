package com.glebalekseevjk.yasmrhomework.ui.fragment

import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.glebalekseevjk.yasmrhomework.R
import com.glebalekseevjk.yasmrhomework.domain.entity.ResultStatus
import com.glebalekseevjk.yasmrhomework.domain.entity.TodoListViewState
import com.glebalekseevjk.yasmrhomework.domain.entity.TodoListViewState.Companion.OK
import com.glebalekseevjk.yasmrhomework.ui.application.MainApplication
import com.glebalekseevjk.yasmrhomework.ui.rv.adapter.TaskListAdapter
import com.glebalekseevjk.yasmrhomework.ui.rv.callback.SwipeController
import com.glebalekseevjk.yasmrhomework.ui.rv.callback.SwipeControllerActions
import com.glebalekseevjk.yasmrhomework.ui.rv.listener.OnTouchListener
import com.glebalekseevjk.yasmrhomework.ui.rv.listener.OnTouchListener.Companion.TouchEventSettings
import com.glebalekseevjk.yasmrhomework.ui.viewmodel.TodoListViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TodoListFragment : Fragment() {
    private val mainApplication: MainApplication by lazy {
        requireContext().applicationContext as MainApplication
    }
    private val todoListViewModel by lazy {
        ViewModelProvider(
            this,
            (context?.applicationContext as MainApplication).todoListViewModelFactory
        )[TodoListViewModel::class.java]
    }
    private lateinit var headerLl: LinearLayout
    private lateinit var headerCountTv: TextView
    private lateinit var taskListRv: RecyclerView
    private lateinit var addTaskBtn: FloatingActionButton
    private lateinit var headerViewIv: ImageView
    private lateinit var taskListSrl: SwipeRefreshLayout
    private lateinit var navController: NavController

    private val dp: Float by lazy { resources.displayMetrics.density }
    private lateinit var taskListAdapter: TaskListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        synchronizeTodoList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_todo_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initErrorHandler()
        initViews(view)
        initListeners()
        initDispatchTouchEventSettings()
        setupRecyclerView()
        observeViewModel()
    }

    private fun initErrorHandler() {
        lifecycleScope.launch {
            todoListViewModel.errorHandler.collect {
                if (it != OK) {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeViewModel() {
        observeSubmitListAdapter()
    }

    private fun synchronizeTodoList() {
        todoListViewModel.synchronizeTodoList {
            when (it.status) {
                ResultStatus.FAILURE -> {
                    todoListViewModel      .setupCheckSynchronizedWorker()
                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                }
                ResultStatus.UNAUTHORIZED -> {
                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                    checkAuth()
                }
                else ->{}
            }
        }
    }

    private fun observeSubmitListAdapter() {
        lifecycleScope.launch {
            with(todoListViewModel) {
                todoListViewState
                    .combine(isViewFinished) { state, isViewFinished ->
                        Pair(state, isViewFinished)
                    }
                    .collect { (state, isViewFinished) ->
                        submitListAdapter(state, isViewFinished)
                    }
            }
        }
    }

    private fun submitListAdapter(state: TodoListViewState, isViewFinished: Boolean) {
        when (state.result.status) {
            ResultStatus.SUCCESS -> {
                val list = state.result.data
                val newTaskList = if (isViewFinished) list.filter { !it.done } else list
                headerCountTv.text = String.format(
                    resources.getString(R.string.count_done),
                    newTaskList.size
                )
                taskListAdapter.submitList(newTaskList.toList())
            }
            ResultStatus.LOADING -> {
            }
            ResultStatus.FAILURE -> {
                todoListViewModel.setupCheckSynchronizedWorker()
                Toast.makeText(context, state.result.message, Toast.LENGTH_SHORT).show()
            }
            ResultStatus.UNAUTHORIZED -> {
                checkAuth()
                Toast.makeText(context, state.result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initViews(view: View) {
        with(view) {
            headerLl = findViewById(R.id.header_ll)
            headerCountTv = findViewById(R.id.header_count_tv)
            taskListRv = findViewById(R.id.task_list_rv)
            addTaskBtn = findViewById(R.id.add_task_btn)
            headerViewIv = findViewById(R.id.header_view_iv)
            taskListSrl = findViewById(R.id.task_list_srl)
            navController = findNavController()
        }
    }

    private fun initListeners() {
        addTaskBtn.setOnClickListener {
            val bundle = bundleOf(TodoFragment.SCREEN_MODE to TodoFragment.MODE_ADD)
            navController.navigate(R.id.action_todoListFragment_to_todoFragment,bundle)
        }
        headerViewIv.setOnClickListener {
            todoListViewModel.isViewFinished.value = !todoListViewModel.isViewFinished.value
        }
        taskListSrl.setOnRefreshListener {
            todoListViewModel.synchronizeTodoList {
                when (it.status) {
                    ResultStatus.SUCCESS -> {
                        taskListSrl.isRefreshing = false
                        Toast.makeText(context, "Успех", Toast.LENGTH_SHORT).show()
                    }
                    ResultStatus.LOADING -> {
                    }
                    ResultStatus.FAILURE -> {
                        todoListViewModel.setupCheckSynchronizedWorker()
                        taskListSrl.isRefreshing = false
                        Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                    }
                    ResultStatus.UNAUTHORIZED -> {
                        checkAuth()
                        taskListSrl.isRefreshing = false
                        Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        taskListAdapter = TaskListAdapter()
        with(taskListRv) {
            adapter = taskListAdapter
            val swipeController = SwipeController(object : SwipeControllerActions() {
                override fun onLeftClicked(position: Int) {
                    todoListViewModel.finishTodo(taskListAdapter.currentList[position]) {
                        when (it.status) {
                            ResultStatus.SUCCESS -> {
                            }
                            ResultStatus.LOADING -> {
                            }
                            ResultStatus.FAILURE -> {
                                todoListViewModel.setupCheckSynchronizedWorker()
                                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                            }
                            ResultStatus.UNAUTHORIZED -> {
                                checkAuth()
                                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onRightClicked(position: Int) {
                    todoListViewModel.deleteTodo(taskListAdapter.currentList[position],{
                        val snackBar: Snackbar = Snackbar.make(taskListRv,"Удаление через 3..",3000)
                        var status = false
                        val mutex = Mutex(locked = true)
                        snackBar.setAction("Отменить"){
                            status = true
                            if (mutex.isLocked){
                                mutex.unlock()
                            }
                        }
                        snackBar.show()
                        lifecycleScope.launch {
                            delay(1000)
                            snackBar.setText("Удаление через 2..")
                            delay(1000)
                            snackBar.setText("Удаление через 1..")
                            delay(1000)
                            if (mutex.isLocked){
                                mutex.unlock()
                            }
                        }
                        mutex.withLock {
                            status
                        }
                    }) {
                        when (it.status) {
                            ResultStatus.SUCCESS -> {
                            }
                            ResultStatus.LOADING -> {
                            }
                            ResultStatus.FAILURE -> {
                                todoListViewModel.setupCheckSynchronizedWorker()
                                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                            }
                            ResultStatus.UNAUTHORIZED -> {
                                checkAuth()
                                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
            ItemTouchHelper(swipeController).attachToRecyclerView(this)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    swipeController.onDraw(c)
                }
            })
            setOnTouchListener(
                OnTouchListener(
                    headerLl,
                    headerCountTv,
                    dp
                )
            )
            taskListAdapter.editClickListener = { id ->
                val bundle = bundleOf(TodoFragment.SCREEN_MODE to TodoFragment.MODE_EDIT,TodoFragment.TODO_ID to id)
                navController.navigate(R.id.action_todoListFragment_to_todoFragment,bundle)
            }
        }
    }

    private fun initDispatchTouchEventSettings() {
        TouchEventSettings.maxPaddingTop = (90 * dp).toInt() + 1
        TouchEventSettings.minPaddingTop = (15 * dp).toInt()
    }

    private fun checkAuth() {
        if (!todoListViewModel.isAuth) {
            navController.navigate(R.id.action_todoListFragment_to_authFragment)
        }
    }
}