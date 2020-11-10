const tags = require('./tags')

exports.init = async (client) => {
  tags.initTags()

  client.on('message', async (msg) => {
    const args = msg.content.split(' ')

    // Run the relevent function for the command
    switch (args[0]) {
      case '!tags':
        await tags.handleTagsCommand(msg, args)
        break
      case '!tag':
        await tags.handleTagCommand(msg, args)
        break
    }
  })
}
