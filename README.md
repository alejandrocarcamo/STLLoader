# ExperimentalStlLoader
A simple class to load STL for JavaFX. There is no opensource STL loader for JavaFX simple to include in your projects. You could use the [interactivemesh](http://www.interactivemesh.org/) jars (they work great) but they are closed source.

This is my approach to cover that gap.

To test the STLLoader I used several Thingiverse models:
* [Fantasy castle](https://www.thingiverse.com/thing:862724): a 116 MB beast to make it suffer (it loads in an average of 17.25 seconds with my i5-5200U)
* [Slack Lack - The Lack Enclosure](https://www.thingiverse.com/thing:3485510)
* [Battery_Dispenser - 24x AA - Stackable](https://www.thingiverse.com/thing:1894851)
* [Modular castle](https://www.thingiverse.com/thing:1930665)
* And some more (my tests included 95 different thingiverse models)
