# Variables

Variables let you insert dynamic values into Injects so that each Player receives personalized content. OpenAEV provides built-in variables for common fields (email address, exercise name, etc.) and lets you create custom variables for anything else.

## Why use Variables?

- Personalize email and SMS Injects with Player-specific data without duplicating content.
- Define reusable tokens that keep Scenario definitions clean and maintainable.
- Combine variables with FreeMarker list syntax to iterate over Teams or articles.

## Built-in variables

Within certain Injects, you can use a set of predefined built-in variables to dynamically customize content.
Examples of built-in variables include:

- **${user.email}**: Represents the email of the target user
- **${exercise.name}**: Represents the name of the current exercise
- **${player_uri}**: Represents the player interface platform link
- **${teams}**: Represents the list of team name/s for the injection


The list of **available variables** is found in the definition of the inject :

![Variables section](../../assets/variables_inject_definition.png)
![Variables section](../../assets/variables_list.png)

## Custom variables

In addition to the built-in variables, users can define their own variables within an exercise.

To define custom variables :

1. Select an exercise
2. Navigate to the Definition tab
3. Navigate to the Variables section

In this section, users can create, update or delete custom variables : 

![Variables section](../../assets/variables_management.png)
![Variables section](../../assets/variables_creation.png)

### Limitations

To create custom variables, consider the following limitation:

- Only lowercase characters and ```_``` are authorized for the key value
- Variable value can only be string

## Use variables

These variables can be used to enhance personalization of certain injects within an exercise.
Here is a non-exhaustive list of concerned injects :
- Email sending
- Sms sending

![Variables usage](../../assets/variables_usage.png)
![Variables usage](../../assets/variables_usage_in_email.png)

In case of a list like `articles`, which is a list of articles with properties such as `id`, `name`, and `uri`, or `${teams}`, you could write:

```freemarker
<#list articles as article> - `${article.name}` </#list>
<#list teams as team> `${team}` </#list>
```

## What's next?

- [Scenarios](../scenario/scenario.md) -- Define custom Variables for your Scenarios
- [Injects](../../evaluate/injects/inject-overview.md) -- Use Variables to personalize Inject content
- [Built-in Injects](../../environment/injects-builtin.md) -- Overview of Injectors that support Variables (Email, SMS)