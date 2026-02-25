package dev.re7gog.b_sideloader.ui.features.add_app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import dev.re7gog.b_sideloader.R

@Composable
fun AddAppScreen(
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddAppViewModel = hiltViewModel()
) {
    Scaffold(
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            AddAppSearchBar(viewModel, modifier = Modifier.fillMaxWidth())
            /*
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("App name") }
            )
            Button(
                onClick = { viewModel.addApp(onSuccess) },
                enabled = viewModel.canAdd
            ) {
                Text("Add app")
            }
            */
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppSearchBar(
    viewModel: AddAppViewModel,
    modifier: Modifier = Modifier
) {
    SearchBar(
        modifier = modifier,
        inputField = {
            SearchBarDefaults.InputField(
                query = viewModel.searchQuery,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSearch = { viewModel.isSearchExpanded = false },
                expanded = viewModel.isSearchExpanded,
                onExpandedChange = { viewModel.isSearchExpanded = it },
                placeholder = { Text(stringResource(R.string.add)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.search_24px),
                        contentDescription = null
                    )
                }
            )
        },
        expanded = viewModel.isSearchExpanded,
        onExpandedChange = { viewModel.isSearchExpanded = it }
    ) {
        if (viewModel.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(viewModel.searchResult) { repo ->
                ListItem(
                    headlineContent = { Text(repo.fullName) },
                    supportingContent = { Text(repo.description ?: "No description", maxLines = 1) },
                    leadingContent = {
                        AsyncImage(
                            model = repo.owner.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                        )
                    },
                    /*trailingContent = {
                        IconButton(onClick = { viewModel.addRepoToDatabase(repo, onAppAdded) }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    },*/
                    //modifier = Modifier.clickable {  }
                )
            }
        }

    }
}