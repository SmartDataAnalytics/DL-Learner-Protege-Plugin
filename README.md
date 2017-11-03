# DL-Learner Protégé Plugin
A Protégé plugin for the DL-Learner framework

## Features

Learn OWL axioms based on instance data.

The following axioms are currently supported via the Protégé editor:

* Class Axioms:
   * [SubClassOf](https://www.w3.org/TR/owl2-syntax/#Subclass_Axioms) with the subclass beeing an OWL class
   * [EquivalentClasses](https://www.w3.org/TR/owl2-syntax/#Equivalent_Classes)
* Object Property Axioms:
   * [Object Subproperties](https://www.w3.org/TR/owl2-syntax/#Object_Subproperties)
   * [Equivalent Object Properties](https://www.w3.org/TR/owl2-syntax/#Equivalent_Object_Properties)
   * [Object Property Domain](https://www.w3.org/TR/owl2-syntax/#Object_Property_Domain)
   * [Object Property Range](https://www.w3.org/TR/owl2-syntax/#Object_Property_Range)
* Data Property Axioms
   * [Data Subproperties](https://www.w3.org/TR/owl2-syntax/#Data_Subproperties)
   * [Equivalent Data Properties](https://www.w3.org/TR/owl2-syntax/#Equivalent_Data_Properties)
   * [Data Property Domain](https://www.w3.org/TR/owl2-syntax/#Data_Property_Domain)
   * [Data Property Range](https://www.w3.org/TR/owl2-syntax/#Data_Property_Range)

## Requirements

The current plugin only works with Protégé 5.0 and newer.

## Installation

### Protege Autoupdate

### Manually from Github



## Usage
![alt tag](https://github.com/AKSW/DL-Learner-Protege-Plugin/raw/develop/doc/images/step_1.png)

 1) Choose a class in the class hierarchy view
 
 ![alt tag](https://github.com/AKSW/DL-Learner-Protege-Plugin/raw/develop/doc/images/step_2.png)
 
 2) Depending on whether you want to search for equivalent class or a super class expressions, i.e. click on the corresponding Add-button ('Equivalent To +')
 
 ![alt tag](https://github.com/AKSW/DL-Learner-Protege-Plugin/raw/develop/doc/images/step_3.png)
 
 3) Choose the DL-Learner tab in the new dialog. Then, click on the 'suggest equivalent class expressions' (or 'suggest super class expressions') button to start the learning process
 
 ![alt tag](https://github.com/AKSW/DL-Learner-Protege-Plugin/raw/develop/doc/images/step_4.png)
 
 4) During algorithm runtime, the list of suggested class expressions will periodically be updated. Once the learning has been finished, you can choose the appropriate class expression(s) and click on the 'Add' button to add them to the current ontology
 
 ![alt tag](https://github.com/AKSW/DL-Learner-Protege-Plugin/raw/develop/doc/images/step_5.png)
 

