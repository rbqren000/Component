package com.ehi.component;

import com.ehi.component.anno.EHiParameterAnno;
import com.ehi.component.anno.EHiRouterAnno;
import com.ehi.component.bean.RouterBean;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
@SupportedOptions("HOST")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes({ComponentUtil.ROUTER_ANNO_CLASS_NAME})
public class RouterProcessor extends BaseHostProcessor {

    private static final int ANNO_TARGET_INVALID = -1;
    private static final String ROUTER_BEAN_NAME = "com.ehi.component.bean.EHiRouterBean";
    private static final String CUSTOMER_INTENT_CALL_CLASS_NAME = "com.ehi.component.bean.CustomerIntentCall";
    private static final String CUSTOMER_JUMP_CLASS_NAME = "com.ehi.component.bean.CustomerJump";
    private static final String ROUTER_REQUEST_CLASS_NAME = "com.ehi.component.impl.EHiRouterRequest";

    private TypeElement customerIntentCallTypeElement;
    private TypeElement customerJumpTypeElement;
    private ClassName customerIntentCallClassName;
    private ClassName customerJumpClassName;

    private TypeElement routerBeanTypeElement;
    private TypeName routerBeanTypeName;
    private TypeElement exceptionTypeElement;
    private ClassName exceptionClassName;
    private TypeElement intentTypeElement;
    private TypeElement mapTypeElement;
    private ClassName mapClassName;
    private TypeMirror intentTypeMirror;
    private TypeElement routerRequestTypeElement;
    private TypeMirror routerRequestTypeMirror;
    private TypeElement queryParameterSupportTypeElement;
    private TypeMirror queryParameterSupportTypeMirror;
    private TypeElement parameterSupportTypeElement;
    private TypeMirror parameterSupportTypeMirror;
    private TypeElement serializableTypeElement;
    private TypeMirror serializableTypeMirror;
    private TypeElement parcelableTypeElement;
    private TypeMirror parcelableTypeMirror;

    // 在每一个 module 中配置的 HOST 的信息
    private String componentHost = null;

    final AtomicInteger atomicInteger = new AtomicInteger();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        mFiler = processingEnv.getFiler();
        mMessager = processingEnvironment.getMessager();
        mTypes = processingEnv.getTypeUtils();
        mElements = processingEnv.getElementUtils();

        routerBeanTypeElement = mElements.getTypeElement(ROUTER_BEAN_NAME);
        routerBeanTypeName = TypeName.get(routerBeanTypeElement.asType());
        mapTypeElement = mElements.getTypeElement(ComponentConstants.JAVA_MAP);
        mapClassName = ClassName.get(mapTypeElement);
        exceptionTypeElement = mElements.getTypeElement(ComponentConstants.JAVA_EXCEPTION);
        exceptionClassName = ClassName.get(exceptionTypeElement);
        customerIntentCallTypeElement = mElements.getTypeElement(CUSTOMER_INTENT_CALL_CLASS_NAME);
        customerJumpTypeElement = mElements.getTypeElement(CUSTOMER_JUMP_CLASS_NAME);
        intentTypeElement = mElements.getTypeElement(ComponentConstants.ANDROID_INTENT);
        intentTypeMirror = intentTypeElement.asType();
        routerRequestTypeElement = mElements.getTypeElement(ROUTER_REQUEST_CLASS_NAME);
        routerRequestTypeMirror = routerRequestTypeElement.asType();
        customerIntentCallClassName = ClassName.get(customerIntentCallTypeElement);
        customerJumpClassName = ClassName.get(customerJumpTypeElement);
        queryParameterSupportTypeElement = mElements.getTypeElement(ComponentConstants.QUERYPARAMETERSUPPORT_CLASS_NAME);
        queryParameterSupportTypeMirror = queryParameterSupportTypeElement.asType();
        parameterSupportTypeElement = mElements.getTypeElement(ComponentConstants.PARAMETERSUPPORT_CLASS_NAME);
        parameterSupportTypeMirror = parameterSupportTypeElement.asType();
        serializableTypeElement = mElements.getTypeElement(ComponentConstants.JAVA_SERIALIZABLE);
        serializableTypeMirror = serializableTypeElement.asType();
        parcelableTypeElement = mElements.getTypeElement(ComponentConstants.ANDROID_PARCELABLE);
        parcelableTypeMirror = parcelableTypeElement.asType();

        Map<String, String> options = processingEnv.getOptions();
        if (options != null) {
            componentHost = options.get("HOST");
        }

        if (componentHost == null || "".equals(componentHost)) {
            ErrorPrintUtil.printHostNull(mMessager);
            return;
        }

        mMessager.printMessage(Diagnostic.Kind.NOTE, "RouterProcessor.componentHost = " + componentHost);

    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {

        if (CollectionUtils.isNotEmpty(set)) {

            Set<? extends Element> routeElements = roundEnvironment.getElementsAnnotatedWith(EHiRouterAnno.class);

            mMessager.printMessage(Diagnostic.Kind.NOTE, " >>> size = " + (routeElements == null ? 0 : routeElements.size()));

            parseRouterAnno(routeElements);

            createRouterImpl();

            return true;
        }

        return false;

    }

    private Map<String, RouterBean> routerMap = new HashMap<>();

    /**
     * 解析注解
     *
     * @param routeElements
     */
    private void parseRouterAnno(Set<? extends Element> routeElements) {

        TypeMirror typeActivity = mElements.getTypeElement(ComponentConstants.ACTIVITY).asType();

        for (Element element : routeElements) {

            mMessager.printMessage(Diagnostic.Kind.NOTE, "element == " + element.toString());

            // 必须标记的是一种类型的元素或者是一个可执行的方法
//            if (!(element instanceof TypeElement)) {
//
//                throw new RuntimeException(element + " is not a 'TypeElement' ");
//
//            }
//
//            // 必须标记在 Activity 上
//            if (!mTypes.isSubtype(tm, typeActivity)) {
//
//                throw new RuntimeException(element + " can't use 'EHiRouterAnno' annotation");
//
//            }

            // 如果是一个Activity 才会走到这里

            EHiRouterAnno router = element.getAnnotation(EHiRouterAnno.class);

            if (router == null) {
                continue;
            }

            if (router.value() == null || "".equals(router.value())) {

                mMessager.printMessage(Diagnostic.Kind.ERROR, element + "：EHiRouterAnno'value can;t be null or empty string");
                continue;

            }

            // 如果有host那就必须满足规范
            if (router.host() == null || "".equals(router.host())) {
            } else {
                if (router.host().contains("/")) {
                    mMessager.printMessage(Diagnostic.Kind.ERROR, "the host value '" + router.host() + "' can't contains '/'");
                }
            }

            if (routerMap.containsKey(getHostAndPath(router.host(), router.value()))) {
                mMessager.printMessage(Diagnostic.Kind.ERROR, element + "：EHiRouterAnno'value is alreay exist");
                continue;

            }

            RouterBean routerBean = new RouterBean();
            routerBean.setHost(router.host());
            routerBean.setPath(router.value());
            routerBean.setDesc(router.desc());
            routerBean.setRawType(element);
            routerBean.getInterceptors().clear();
            routerBean.getInterceptors().addAll(getImplClassName(router));
            if (router.interceptorNames() != null) {
                routerBean.getInterceptorNames().clear();
                for (String interceptorName : router.interceptorNames()) {
                    routerBean.getInterceptorNames().add(interceptorName);
                }
            }

            routerMap.put(getHostAndPath(router.host(), router.value()), routerBean);

            mMessager.printMessage(Diagnostic.Kind.NOTE, "router.value() = " + router.value() + ",Activity = " + element);

        }

    }

    /**
     * 生成路由
     */
    private void createRouterImpl() {

        String claName = ComponentUtil.genHostRouterClassName(componentHost);

        //pkg
        String pkg = claName.substring(0, claName.lastIndexOf("."));

        //simpleName
        String cn = claName.substring(claName.lastIndexOf(".") + 1);

        // superClassName
        ClassName superClass = ClassName.get(mElements.getTypeElement(ComponentUtil.UIROUTER_IMPL_CLASS_NAME));

        MethodSpec initHostMethod = generateInitHostMethod();
        MethodSpec initMapMethod = generateInitMapMethod();

        TypeSpec typeSpec = TypeSpec.classBuilder(cn)
                //.addModifiers(Modifier.PUBLIC)
                .addModifiers(Modifier.FINAL)
                .superclass(superClass)
                .addMethod(initHostMethod)
                .addMethod(initMapMethod)
                .addJavadoc(componentHost + "业务模块的路由表\n")
                .build();

        try {
            JavaFile.builder(pkg, typeSpec)
                    .indent("    ")
                    .build().writeTo(mFiler);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private MethodSpec generateInitMapMethod() {
        TypeName returnType = TypeName.VOID;

        final MethodSpec.Builder initMapMethodSpecBuilder = MethodSpec.methodBuilder("initMap")
                .returns(returnType)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC);

        initMapMethodSpecBuilder.addStatement("super.initMap()");

        routerMap.forEach(new BiConsumer<String, com.ehi.component.bean.RouterBean>() {
            @Override
            public void accept(String key, RouterBean routerBean) {

                // 1. 如果标记在 Activity 上
                // 可能是空的,因为注解可能标记在静态方法上
                ClassName targetActivityClassName = null;

                // 2. 如果标记在静态方法上
                // 当标记在静态方法上的时候,这个不会为空,比如 "xxx.intentCreate"
                String customerIntentOrJumpPath = null;

                // 注解标记到的界面的类型
                int annoTarget = ANNO_TARGET_INVALID;

                // 注释的文本,无论哪种情况都不会为空
                String commentStr = null;
                // 生成变量的名字,每一个变量代表每一个目标界面的配置对象
                String routerBeanName = "routerBean" + atomicInteger.incrementAndGet();
                // 生成 Activity 的调用代码
                generateActivityCall(routerBean, routerBeanName, initMapMethodSpecBuilder);
                // 生成静态方法的代码的调用
                generateStaticMethodCall(routerBean, routerBeanName, initMapMethodSpecBuilder);
                // 拦截器的代码的生成
                if (routerBean.getInterceptors() != null && routerBean.getInterceptors().size() > 0) {
                    initMapMethodSpecBuilder.addStatement("$N.interceptors = new $T($L)", routerBeanName, ArrayList.class, routerBean.getInterceptors().size());
                    for (String interceptorClassName : routerBean.getInterceptors()) {
                        initMapMethodSpecBuilder.addStatement("$N.interceptors.add($T.class)", routerBeanName, ClassName.get(mElements.getTypeElement(interceptorClassName)));
                    }
                }
                if (routerBean.getInterceptorNames() != null && routerBean.getInterceptorNames().size() > 0) {
                    initMapMethodSpecBuilder.addStatement("$N.interceptorNames = new $T($L)", routerBeanName, ArrayList.class, routerBean.getInterceptorNames().size());
                    for (String interceptorName : routerBean.getInterceptorNames()) {
                        initMapMethodSpecBuilder.addStatement("$N.interceptorNames.add($S)", routerBeanName, interceptorName);
                    }
                }
                initMapMethodSpecBuilder.addStatement("routerBeanMap.put($S,$N)", key, routerBeanName);
                initMapMethodSpecBuilder.addCode("\n");
            }
        });

        initMapMethodSpecBuilder.addJavadoc("初始化路由表的数据\n");

        return initMapMethodSpecBuilder.build();
    }

    private MethodSpec generateInitHostMethod() {

        TypeName returnType = mClassNameString;

        MethodSpec.Builder openUriMethodSpecBuilder = MethodSpec.methodBuilder("getHost")
                .returns(returnType)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC);

        openUriMethodSpecBuilder.addStatement("return $S", componentHost);

        return openUriMethodSpecBuilder.build();
    }

    private String getHostAndPath(String host, String path) {

        if (host == null || "".equals(host)) {
            host = componentHost;
        }

        if (path != null && path.length() > 0 && path.charAt(0) != '/') {
            path = "/" + path;
        }

        return host + path;

    }

    private List<String> getImplClassName(EHiRouterAnno anno) {
        List<String> implClassNames = new ArrayList<>();
        try {
            implClassNames.clear();
            Class[] interceptors = anno.interceptors();
            for (Class interceptor : interceptors) {
                implClassNames.add(interceptor.getName());
            }
        } catch (MirroredTypesException e) {
            implClassNames.clear();
            List<? extends TypeMirror> typeMirrors = e.getTypeMirrors();
            for (TypeMirror typeMirror : typeMirrors) {
                implClassNames.add(typeMirror.toString());
            }
        }
        return implClassNames;
    }

    /**
     * 生成目标是 Activity 的调用
     *
     * @param routerBean
     * @param methodSpecBuilder
     */
    private void generateActivityCall(RouterBean routerBean, String routerBeanName, MethodSpec.Builder methodSpecBuilder) {
        if (!(routerBean.getRawType() instanceof TypeElement)) {
            return;
        }
        ClassName targetActivityClassName = ClassName.get((TypeElement) routerBean.getRawType());
        String commentStr = targetActivityClassName.toString();
        methodSpecBuilder.addCode("\n");
        methodSpecBuilder.addComment("---------------------------" + commentStr + "---------------------------");
        methodSpecBuilder.addCode("\n");
        methodSpecBuilder.addStatement("$T $N = new $T()", routerBeanTypeElement, routerBeanName, routerBeanTypeElement);
        methodSpecBuilder.addStatement("$N.host = $S", routerBeanName, routerBean.getHost());
        methodSpecBuilder.addStatement("$N.path = $S", routerBeanName, routerBean.getPath());
        methodSpecBuilder.addStatement("$N.desc = $S", routerBeanName, routerBean.getDesc());
        methodSpecBuilder.addStatement("$N.targetClass = $T.class", routerBeanName, targetActivityClassName);
    }

    /**
     * 目的是生成调用静态方法的实现
     *
     * @param routerBean        注解的实体对象
     * @param methodSpecBuilder 生成方法代码的 Builder
     * @return
     */
    private void generateStaticMethodCall(RouterBean routerBean, String routerBeanName, MethodSpec.Builder methodSpecBuilder) {
        if (!(routerBean.getRawType() instanceof ExecutableElement)) {
            return;
        }
        // 静态方法
        ExecutableElement executableElement = (ExecutableElement) routerBean.getRawType();
        // 获取声明这个方法的类的 TypeElement
        TypeElement declareClassType = (TypeElement) executableElement.getEnclosingElement();
        // 调用这个静态方法的全路径
        String customerIntentOrJumpPath = declareClassType.toString() + "." + executableElement.getSimpleName();
        // 注释的信息
        String commentStr = customerIntentOrJumpPath;
        // 获取自定义的静态方法的返回类型
        TypeMirror customerReturnType = executableElement.getReturnType();

        methodSpecBuilder.addCode("\n");
        methodSpecBuilder.addComment("---------------------------" + commentStr + "---------------------------");
        methodSpecBuilder.addCode("\n");
        methodSpecBuilder.addStatement("$T $N = new $T()", routerBeanTypeElement, routerBeanName, routerBeanTypeElement);
        methodSpecBuilder.addStatement("$N.host = $S", routerBeanName, routerBean.getHost());
        methodSpecBuilder.addStatement("$N.path = $S", routerBeanName, routerBean.getPath());
        methodSpecBuilder.addStatement("$N.desc = $S", routerBeanName, routerBean.getDesc());

        // 如果是自定义 Intent
        if (intentTypeMirror.equals(customerReturnType)) {

            MethodSpec.Builder jumpMethodBuilder = MethodSpec.methodBuilder("get")
                    .addParameter(TypeName.get(routerRequestTypeMirror), "request", Modifier.FINAL)
                    .addAnnotation(Override.class)
                    .addException(exceptionClassName)
                    .addModifiers(Modifier.PUBLIC);
            generateActualMethodCall(jumpMethodBuilder, executableElement, customerIntentOrJumpPath, true);
            jumpMethodBuilder.returns(TypeName.get(intentTypeElement.asType()));

            TypeSpec intentCallTypeSpec = TypeSpec.anonymousClassBuilder("")
                    .addSuperinterface(customerIntentCallClassName)
                    .addMethod(
                            jumpMethodBuilder.build()
                    )
                    .build();
            // 添加一个匿名内部类
            methodSpecBuilder.addStatement("$N.customerIntentCall = $L", routerBeanName, intentCallTypeSpec);
        } else { // 自定义跳转的

            MethodSpec.Builder jumpMethodBuilder = MethodSpec.methodBuilder("jump")
                    .addParameter(TypeName.get(routerRequestTypeMirror), "request", Modifier.FINAL)
                    .addAnnotation(Override.class)
                    .addException(exceptionClassName)
                    .addModifiers(Modifier.PUBLIC);

            generateActualMethodCall(jumpMethodBuilder, executableElement, customerIntentOrJumpPath, false);

            TypeSpec customerJumpTypeSpec = TypeSpec.anonymousClassBuilder("")
                    .addSuperinterface(customerJumpClassName)
                    .addMethod(
                            jumpMethodBuilder.build()
                    )
                    .build();
            methodSpecBuilder.addStatement("$N.customerJump = $L", routerBeanName, customerJumpTypeSpec);
        }

        /*mMessager.printMessage(Diagnostic.Kind.NOTE, "generateStaticMethodCall.element = start");
        StringBuffer sb = new StringBuffer();
        if (parameters != null && parameters.size() > 0) {
            for (int i = 0; i < parameters.size(); i++) {
                // 拿到每一个参数
                VariableElement variableElement = parameters.get(i);

                ClassName.INT
                mMessager.printMessage(Diagnostic.Kind.NOTE, "generateStaticMethodCall.element = " + ClassName.get(variableElement.asType()));
                mMessager.printMessage(Diagnostic.Kind.NOTE, "generateStaticMethodCall.element = " + variableElement.asType().equals(routerRequestTypeMirror));
            }
        }
        return sb.toString();*/
    }

    private void generateActualMethodCall(MethodSpec.Builder jumpMethodBuilder, ExecutableElement executableElement,
                                          String customerIntentOrJumpPath, boolean isReturnIntent) {
        // 获取这个方法的参数
        List<? extends VariableElement> parameters = executableElement.getParameters();
        // 参数调用的 sb
        StringBuffer parameterSB = new StringBuffer();
        if (parameters != null && parameters.size() > 0) {
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    parameterSB.append(",");
                }
                // 拿到每一个参数
                VariableElement variableElement = parameters.get(i);
                TypeName parameterTypeName = ClassName.get(variableElement.asType());
                // 生成一个不重复的参数名字
                String parameterName = "paramater" + atomicInteger.incrementAndGet();
                // 如果要的是 request 对象
                if (variableElement.asType().equals(routerRequestTypeMirror)) {
                    parameterSB.append("request");
                } else if (parameterTypeName.equals(mClassNameString)) { // 如果是一个 String
                    EHiParameterAnno parameterAnno = variableElement.getAnnotation(EHiParameterAnno.class);
                    jumpMethodBuilder.addStatement("String $N = $T.getString(request.bundle,$S)", parameterName, parameterSupportTypeMirror, parameterAnno.value());
                    parameterSB.append(parameterName);
                } else if (parameterTypeName.equals(ClassName.BYTE) || parameterTypeName.equals(ClassName.BYTE.box())) { // 如果是一个byte or Byte
                    EHiParameterAnno parameterAnno = variableElement.getAnnotation(EHiParameterAnno.class);
                    jumpMethodBuilder.addStatement("byte $N = $T.getByte(request.bundle,$S,(byte)$L)", parameterName, parameterSupportTypeMirror, parameterAnno.value(), parameterAnno.byteDefault());
                    parameterSB.append(parameterName);
                } else if (parameterTypeName.equals(ClassName.SHORT) || parameterTypeName.equals(ClassName.SHORT.box())) { // 如果是一个short or Short
                    EHiParameterAnno parameterAnno = variableElement.getAnnotation(EHiParameterAnno.class);
                    jumpMethodBuilder.addStatement("short $N = $T.getShort(request.bundle,$S,(short)$L)", parameterName, parameterSupportTypeMirror, parameterAnno.value(), parameterAnno.shortDefault());
                    parameterSB.append(parameterName);
                } else if (parameterTypeName.equals(ClassName.INT) || parameterTypeName.equals(ClassName.INT.box())) { // 如果是一个int or Integer
                    EHiParameterAnno parameterAnno = variableElement.getAnnotation(EHiParameterAnno.class);
                    jumpMethodBuilder.addStatement("int $N = $T.getInt(request.bundle,$S,$L)", parameterName, parameterSupportTypeMirror, parameterAnno.value(), parameterAnno.intDefault());
                    parameterSB.append(parameterName);
                } else if (parameterTypeName.equals(ClassName.LONG) || parameterTypeName.equals(ClassName.LONG.box())) { // 如果是一个long or Long
                    EHiParameterAnno parameterAnno = variableElement.getAnnotation(EHiParameterAnno.class);
                    jumpMethodBuilder.addStatement("long $N = $T.getLong(request.bundle,$S,$Ll)", parameterName, parameterSupportTypeMirror, parameterAnno.value(), parameterAnno.longDefault());
                    parameterSB.append(parameterName);
                } else if (parameterTypeName.equals(ClassName.FLOAT) || parameterTypeName.equals(ClassName.FLOAT.box())) { // 如果是一个float or Float
                    EHiParameterAnno parameterAnno = variableElement.getAnnotation(EHiParameterAnno.class);
                    jumpMethodBuilder.addStatement("float $N = $T.getFloat(request.bundle,$S,$Lf)", parameterName, parameterSupportTypeMirror, parameterAnno.value(), parameterAnno.floatDefault());
                    parameterSB.append(parameterName);
                } else if (parameterTypeName.equals(ClassName.DOUBLE) || parameterTypeName.equals(ClassName.DOUBLE.box())) { // 如果是一个double or Double
                    EHiParameterAnno parameterAnno = variableElement.getAnnotation(EHiParameterAnno.class);
                    jumpMethodBuilder.addStatement("double $N = $T.getDouble(request.bundle,$S,$Ld)", parameterName, parameterSupportTypeMirror, parameterAnno.value(), parameterAnno.doubleDefault());
                    parameterSB.append(parameterName);
                } else if (parameterTypeName.equals(ClassName.BOOLEAN) || parameterTypeName.equals(ClassName.BOOLEAN.box())) { // 如果是一个boolean or Boolean
                    EHiParameterAnno parameterAnno = variableElement.getAnnotation(EHiParameterAnno.class);
                    jumpMethodBuilder.addStatement("boolean $N = $T.getBoolean(request.bundle,$S,$L)", parameterName, parameterSupportTypeMirror, parameterAnno.value(), parameterAnno.booleanDefault());
                    parameterSB.append(parameterName);
                } else { // 其他类型的情况,是实现序列化的对象,这种时候我们直接要从 bundle 中获取
                    EHiParameterAnno parameterAnno = variableElement.getAnnotation(EHiParameterAnno.class);
                    jumpMethodBuilder.addStatement("$T $N = null", variableElement.asType(), parameterName);
                    // 优先获取 parcelable
                    if (mTypes.isSubtype(variableElement.asType(), parcelableTypeMirror)) {
                        jumpMethodBuilder.addStatement("$N = ($T) request.bundle.getParcelable($S)", parameterName, variableElement.asType(), parameterAnno.value());
                    }
                    jumpMethodBuilder.beginControlFlow("if ($N == null) ", parameterName);
                    if (mTypes.isSubtype(variableElement.asType(), serializableTypeMirror)) {
                        jumpMethodBuilder.addStatement("$N = ($T) request.bundle.getSerializable($S)", parameterName, variableElement.asType(), parameterAnno.value());
                    }
                    jumpMethodBuilder.endControlFlow();
                    parameterSB.append(parameterName);
                }
            }
        }
        if (isReturnIntent) {
            jumpMethodBuilder.addStatement("return $N($N)", customerIntentOrJumpPath, parameterSB.toString());
        } else {
            jumpMethodBuilder.addStatement("$N($N)", customerIntentOrJumpPath, parameterSB.toString());
        }
    }

    private boolean isHaveDefaultConstructor(String interceptorClassName) {
        // 实现类的类型
        TypeElement typeElementInterceptorImpl = mElements.getTypeElement(interceptorClassName);
        String constructorName = typeElementInterceptorImpl.getSimpleName().toString() + ("()");
        List<? extends Element> enclosedElements = typeElementInterceptorImpl.getEnclosedElements();
        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.toString().equals(constructorName)) {
                return true;
            }
        }
        return false;
    }

}
